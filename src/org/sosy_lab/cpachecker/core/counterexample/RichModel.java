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
package org.sosy_lab.cpachecker.core.counterexample;

import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.ForwardingMap;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.Multimap;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import org.sosy_lab.common.Appender;
import org.sosy_lab.common.Appenders;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.core.counterexample.Model.AssignableTerm;
import org.sosy_lab.cpachecker.cpa.arg.ARGPath;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;

/**
 * This class represents an assignment of concrete values to program variables
 * along a path. Each variable can have several assignments with different
 * SSA indices if it gets re-assigned along the path.
 * The value of each variable can be an arbitrary object, but usually
 * this is a {@link Number}.
 *
 * Unlike {@link Model} class, RichModel contains extra meta-information:
 * {@link CFAPathWithAssumptions} and a multimap from {@link CFAEdge} to objects
 * stored.
 */
public class RichModel extends ForwardingMap<AssignableTerm, Object> implements Appender {

  private final Model mModel;

  private final CFAPathWithAssumptions assignments;
  private final Multimap<CFAEdge, AssignableTerm> assignableTermsPerCFAEdge;

  @Override
  protected Map<AssignableTerm, Object> delegate() {
    return mModel;
  }

  public static RichModel empty() {
    return new RichModel();
  }

  private RichModel() {
    mModel = Model.empty();
    assignments = new CFAPathWithAssumptions(new LinkedList<CFAEdgeWithAssumptions>());
    assignableTermsPerCFAEdge = ImmutableListMultimap.of();
  }

  public RichModel(Map<AssignableTerm, Object> content) {
    mModel = new Model(content);
    assignments = new CFAPathWithAssumptions(new LinkedList<CFAEdgeWithAssumptions>());
    assignableTermsPerCFAEdge = ImmutableListMultimap.of();
  }

  private RichModel(Map<AssignableTerm, Object> content,
      CFAPathWithAssumptions pAssignments) {
    mModel = new Model(content);
    assignments = pAssignments;
    assignableTermsPerCFAEdge = ImmutableListMultimap.of();
  }

  public static RichModel of(Model model) {
    return new RichModel(model);
  }

  /**
   * Return a new model that is equal to the current one,
   * but additionally has information about when each variable was assigned.
   */
  public RichModel withAssignmentInformation(CFAPathWithAssumptions pAssignments) {
    checkState(assignments.isEmpty());
    return new RichModel(mModel, pAssignments);
  }

  /**
   * Return a path that indicates which variables where assigned which values at
   * what edge. Note that not every value for every variable is available.
   */
  @Nullable
  public CFAPathWithAssumptions getCFAPathWithAssignments() {
    return assignments;
  }

  /**
   * Returns a collection of {@link AssignableTerm}} terms that were assigned a the given
   * {@link CFAEdge} edge.
   *
   * @param pEdge All terms that were assigned at this edge are returned-
   * @return A collection of terms assigned at the given edge.
   */
  public Collection<AssignableTerm> getAllAssignedTerms(CFAEdge pEdge) {
    return assignableTermsPerCFAEdge.get(pEdge);
  }

  @Nullable
  public Map<ARGState, Collection<CFAEdgeWithAssumptions>> getExactVariableValues(ARGPath pPath) {

    if (assignments.isEmpty()) {
      return null;
    }

    return assignments.getExactVariableValues(pPath).asMap();
  }

  @Nullable
  public CFAPathWithAssumptions getExactVariableValuePath(List<CFAEdge> pPath) {

    if (assignments.isEmpty()) {
      return null;
    }
    return null;
    //return assignments.getExactVariableValues(pPath);
  }

  @Override
  public void appendTo(Appendable output) throws IOException {
    mModel.appendTo(output);
  }

  @Override
  public String toString() {
    return Appenders.toString(this);
  }
}