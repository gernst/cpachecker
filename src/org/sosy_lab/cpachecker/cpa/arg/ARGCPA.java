/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2014  Dirk Beyer
 *  All rights reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *
 *  CPAchecker web page:
 *    http://cpachecker.sosy-lab.org
 */
package org.sosy_lab.cpachecker.cpa.arg;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.logging.Level;

import org.sosy_lab.common.Classes;
import org.sosy_lab.common.configuration.ClassOption;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.core.CounterexampleInfo;
import org.sosy_lab.cpachecker.core.ShutdownNotifier;
import org.sosy_lab.cpachecker.core.defaults.AbstractSingleWrapperCPA;
import org.sosy_lab.cpachecker.core.defaults.AutomaticCPAFactory;
import org.sosy_lab.cpachecker.core.defaults.FlatLatticeDomain;
import org.sosy_lab.cpachecker.core.defaults.MergeSepOperator;
import org.sosy_lab.cpachecker.core.defaults.SimplePrecisionAdjustment;
import org.sosy_lab.cpachecker.core.interfaces.AbstractDomain;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.CPAFactory;
import org.sosy_lab.cpachecker.core.interfaces.ConfigurableProgramAnalysis;
import org.sosy_lab.cpachecker.core.interfaces.ConfigurableProgramAnalysisWithBAM;
import org.sosy_lab.cpachecker.core.interfaces.ForcedCoveringStopOperator;
import org.sosy_lab.cpachecker.core.interfaces.MergeOperator;
import org.sosy_lab.cpachecker.core.interfaces.PostProcessor;
import org.sosy_lab.cpachecker.core.interfaces.PrecisionAdjustment;
import org.sosy_lab.cpachecker.core.interfaces.Reducer;
import org.sosy_lab.cpachecker.core.interfaces.Statistics;
import org.sosy_lab.cpachecker.core.interfaces.TransferRelation;
import org.sosy_lab.cpachecker.core.interfaces.pcc.ProofChecker;
import org.sosy_lab.cpachecker.cpa.arg.counterexamples.CEXExporter;
import org.sosy_lab.cpachecker.cpa.arg.counterexamples.ConjunctiveCounterexampleFilter;
import org.sosy_lab.cpachecker.cpa.arg.counterexamples.CounterexampleFilter;
import org.sosy_lab.cpachecker.cpa.arg.counterexamples.NullCounterexampleFilter;
import org.sosy_lab.cpachecker.cpa.arg.counterexamples.PathEqualityCounterexampleFilter;
import org.sosy_lab.cpachecker.exceptions.CPAException;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

@Options(prefix="cpa.arg")
public class ARGCPA extends AbstractSingleWrapperCPA implements ConfigurableProgramAnalysisWithBAM, ProofChecker, PostProcessor {

  public static CPAFactory factory() {
    return AutomaticCPAFactory.forType(ARGCPA.class);
  }

  @Option(secure=true,
  description="inform ARG CPA if it is run in a predicated analysis because then it must"
    + "behave differntly during merge.")
  private boolean inPredicatedAnalysis = false;

  @Option(secure=true,
      description = "disable post processor for runtime verification ARG simplification, " +
          "always disable if CPA does not contain an automaton/monitor for error specification")
  private boolean disableRVARGSimplification = false;

  @Option(secure=true, name="errorPath.filters",
      description="Filter for irrelevant counterexamples to reduce the number of similar counterexamples reported."
      + " Only relevant with analysis.stopAfterErrors=false and cpa.arg.errorPath.exportImmediately=true."
      + " Put the weakest and cheapest filter first, e.g., PathEqualityCounterexampleFilter.")
  @ClassOption(packagePrefix="org.sosy_lab.cpachecker.cpa.arg.counterexamples")
  private List<Class<? extends CounterexampleFilter>> cexFilterClasses
      = ImmutableList.<Class<? extends CounterexampleFilter>>of(
          PathEqualityCounterexampleFilter.class);
  private final CounterexampleFilter cexFilter;

  private final LogManager logger;

  private final AbstractDomain abstractDomain;
  private final ARGTransferRelation transferRelation;
  private final MergeOperator mergeOperator;
  private final ARGStopSep stopOperator;
  private final PrecisionAdjustment precisionAdjustment;
  private final Reducer reducer;
  private final ARGStatistics stats;
  private final ProofChecker wrappedProofChecker;
  private final PostProcessor innerPostProcessor;
  private final Collection<PostProcessor> postProcessors;

  private final CEXExporter cexExporter;
  private final Map<ARGState, CounterexampleInfo> counterexamples = new WeakHashMap<>();

  private ARGCPA(ConfigurableProgramAnalysis cpa, Configuration config, LogManager logger, ShutdownNotifier, CFA cfa) throws InvalidConfigurationException {
    super(cpa);
    config.inject(this);
    this.logger = logger;
    abstractDomain = new FlatLatticeDomain();
    transferRelation = new ARGTransferRelation(cpa.getTransferRelation());

    PrecisionAdjustment wrappedPrec = cpa.getPrecisionAdjustment();
    if (wrappedPrec instanceof SimplePrecisionAdjustment) {
      precisionAdjustment = new ARGSimplePrecisionAdjustment((SimplePrecisionAdjustment) wrappedPrec);
    } else {
      precisionAdjustment = new ARGPrecisionAdjustment(cpa.getPrecisionAdjustment(), inPredicatedAnalysis);
    }

    if (cpa instanceof ConfigurableProgramAnalysisWithBAM) {
      Reducer wrappedReducer = ((ConfigurableProgramAnalysisWithBAM)cpa).getReducer();
      if (wrappedReducer != null) {
        reducer = new ARGReducer(wrappedReducer);
      } else {
        reducer = null;
      }
    } else {
      reducer = null;
    }

    if (cpa instanceof ProofChecker) {
      this.wrappedProofChecker = (ProofChecker)cpa;
    } else {
      this.wrappedProofChecker = null;
    }

    MergeOperator wrappedMerge = getWrappedCpa().getMergeOperator();
    if (wrappedMerge == MergeSepOperator.getInstance()) {
      mergeOperator = MergeSepOperator.getInstance();
    } else {
      if (inPredicatedAnalysis) {
        mergeOperator = new ARGMergeJoinPredicatedAnalysis(wrappedMerge);
      } else {
        mergeOperator = new ARGMergeJoin(wrappedMerge);
      }
    }
    stopOperator = new ARGStopSep(getWrappedCpa().getStopOperator(), logger, config);
    cexFilter = createCounterexampleFilter(config, logger, cpa);
    cexExporter = new CEXExporter(config, logger);
    stats = new ARGStatistics(config, this);

    if (cpa instanceof PostProcessor) {
      innerPostProcessor = (PostProcessor)cpa;
    } else {
      innerPostProcessor = null;
    }
    postProcessors = new ArrayList<>();
    postProcessors.add(new ARGDuplicateEdgeRemover());
    if (!disableRVARGSimplification) {
      postProcessors.add(new RVARGSimplifier(config, this, pShutdownNotifier));
    }
    postProcessors.add(new ARGDumper(config, this));

  }

  private CounterexampleFilter createCounterexampleFilter(Configuration config,
      LogManager logger, ConfigurableProgramAnalysis cpa) throws InvalidConfigurationException {
    final Object[] argumentValues = new Object[]{config, logger, cpa};
    final Class<?>[] argumentTypes = new Class<?>[]{Configuration.class, LogManager.class, ConfigurableProgramAnalysis.class};

    switch (cexFilterClasses.size()) {
    case 0:
      return new NullCounterexampleFilter();
    case 1:
      return Classes.createInstance(CounterexampleFilter.class, cexFilterClasses.get(0),
          argumentTypes,
          argumentValues,
          InvalidConfigurationException.class);
    default:
      List<CounterexampleFilter> filters = new ArrayList<>(cexFilterClasses.size());
      for (Class<? extends CounterexampleFilter> cls : cexFilterClasses) {
        filters.add(Classes.createInstance(CounterexampleFilter.class, cls,
          argumentTypes, argumentValues,
          InvalidConfigurationException.class));
      }
      return new ConjunctiveCounterexampleFilter(filters);
    }
  }

  @Override
  public AbstractDomain getAbstractDomain() {
    return abstractDomain;
  }

  @Override
  public TransferRelation getTransferRelation() {
    return transferRelation;
  }

  @Override
  public MergeOperator getMergeOperator() {
    return mergeOperator;
  }

  @Override
  public ForcedCoveringStopOperator getStopOperator() {
    return stopOperator;
  }

  @Override
  public PrecisionAdjustment getPrecisionAdjustment() {
    return precisionAdjustment;
  }

  @Override
  public Reducer getReducer() {
    return reducer;
  }

  @Override
  public AbstractState getInitialState(CFANode pNode) {
    // TODO some code relies on the fact that this method is called only one and the result is the root of the ARG
    return new ARGState(getWrappedCpa().getInitialState(pNode), null);
  }

  protected LogManager getLogger() {
    return logger;
  }

  @Override
  public void collectStatistics(Collection<Statistics> pStatsCollection) {
    pStatsCollection.add(stats);
    super.collectStatistics(pStatsCollection);
  }

  public Map<ARGState, CounterexampleInfo> getCounterexamples() {
    return Collections.unmodifiableMap(counterexamples);
  }

  public void addCounterexample(ARGState targetState, CounterexampleInfo pCounterexample) {
    checkArgument(targetState.isTarget());
    checkArgument(!pCounterexample.isSpurious());
    if (pCounterexample.getTargetPath() != null) {
      // With BAM, the targetState and the last state of the path
      // may actually be not identical.
      checkArgument(pCounterexample.getTargetPath().getLastState().isTarget());
    }
    counterexamples.put(targetState, pCounterexample);
  }

  public void clearCounterexamples(Set<ARGState> toRemove) {
    // Actually the goal would be that this method is not necessary
    // because the GC automatically removes counterexamples when the ARGState
    // is removed from the ReachedSet.
    // However, counterexamples may reference their target state through
    // the target path attribute, so the GC may not remove the counterexample.
    // While this is not a problem for correctness
    // (we check in the end which counterexamples are still valid),
    // it may be a memory leak.
    // Thus this method.

    counterexamples.keySet().removeAll(toRemove);
  }

  ARGToDotWriter getRefinementGraphWriter() {
    return stats.getRefinementGraphWriter();
  }

  @Override
  public boolean areAbstractSuccessors(AbstractState pElement, CFAEdge pCfaEdge,
      Collection<? extends AbstractState> pSuccessors) throws CPATransferException, InterruptedException {
    Preconditions.checkNotNull(wrappedProofChecker, "Wrapped CPA has to implement ProofChecker interface");
    return transferRelation.areAbstractSuccessors(pElement, pCfaEdge, pSuccessors, wrappedProofChecker);
  }

  @Override
  public boolean isCoveredBy(AbstractState pElement, AbstractState pOtherElement) throws CPAException, InterruptedException {
    Preconditions.checkNotNull(wrappedProofChecker, "Wrapped CPA has to implement ProofChecker interface");
    return stopOperator.isCoveredBy(pElement, pOtherElement, wrappedProofChecker);
  }

  @Override
  public void postProcess(ReachedSet pReached) throws InterruptedException {
    if (innerPostProcessor != null) {
      innerPostProcessor.postProcess(pReached);
    }
    for (PostProcessor postProcessor : postProcessors) {
      postProcessor.postProcess(pReached);
    }
  }

  void exportCounterexampleOnTheFly(ARGState pTargetState,
    CounterexampleInfo pCounterexampleInfo, int cexIndex) throws InterruptedException {
    if (cexExporter.shouldDumpErrorPathImmediately()) {
      if (cexFilter.isRelevant(pCounterexampleInfo)) {
        cexExporter.exportCounterexample(pTargetState, pCounterexampleInfo, cexIndex, null, true);
      } else {
        logger.log(Level.FINEST, "Skipping counterexample printing because it is similar to one of already printed.");
      }
    }
  }
}
