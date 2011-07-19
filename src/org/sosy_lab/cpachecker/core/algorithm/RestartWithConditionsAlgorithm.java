/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2011  Dirk Beyer
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
package org.sosy_lab.cpachecker.core.algorithm;

import java.util.Collection;

import org.sosy_lab.common.LogManager;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.cpachecker.core.interfaces.ConfigurableProgramAnalysis;
import org.sosy_lab.cpachecker.core.interfaces.PrecisionAdjustment;
import org.sosy_lab.cpachecker.core.interfaces.Statistics;
import org.sosy_lab.cpachecker.core.interfaces.StatisticsProvider;
import org.sosy_lab.cpachecker.core.interfaces.WrapperCPA;
import org.sosy_lab.cpachecker.core.reachedset.ReachedSet;
import org.sosy_lab.cpachecker.cpa.assumptions.progressobserver.ProgressObserverCPA;
import org.sosy_lab.cpachecker.exceptions.CPAException;

public class RestartWithConditionsAlgorithm implements Algorithm, StatisticsProvider {

  private final Algorithm innerAlgorithm;
  private final LogManager logger;

  public RestartWithConditionsAlgorithm(Algorithm algorithm, Configuration config, LogManager logger) throws InvalidConfigurationException
  {
    config.inject(this);
    this.logger = logger;
    innerAlgorithm = algorithm;
  }

  @Override
  public ConfigurableProgramAnalysis getCPA() {
    return innerAlgorithm.getCPA();
  }

  @Override
  public boolean run(ReachedSet pReached) throws CPAException,
  InterruptedException {
    boolean sound = true;

    boolean restartCPA = false;

    // loop if restartCPA is set to false
    do {
      restartCPA = false;
      // run the inner algorithm to fill the reached set
      sound = innerAlgorithm.run(pReached);

      if(!sound) {
        restartCPA = true;
        ConfigurableProgramAnalysis observerCpa = ((WrapperCPA)getCPA()).retrieveWrappedCpa(ProgressObserverCPA.class);
        PrecisionAdjustment predAdjustment = ((ProgressObserverCPA)observerCpa).getPrecisionAdjustment();
        //predAdjustment.prec(element, precision, elements);
      }

    } while (restartCPA);

    return sound;
  }

  @Override
  public void collectStatistics(Collection<Statistics> pStatsCollection) {
    // TODO Auto-generated method stub

  }
}
