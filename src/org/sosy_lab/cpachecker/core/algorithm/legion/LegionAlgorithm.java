/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2020  Dirk Beyer
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
 */
package org.sosy_lab.cpachecker.core.algorithm.legion;

import java.util.logging.Level;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.core.algorithm.Algorithm;
import org.sosy_lab.cpachecker.core.algorithm.CPAAlgorithm;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.reachedset.ReachedSet;
import org.sosy_lab.cpachecker.exceptions.CPAEnabledAnalysisPropertyViolationException;
import org.sosy_lab.cpachecker.exceptions.CPAException;

import java.util.Iterator;
import java.util.Set;

public class LegionAlgorithm implements Algorithm {
    private final Algorithm algorithm;
    private final LogManager logger;

    public LegionAlgorithm(final Algorithm algorithm, final LogManager pLogger) {
        this.algorithm = algorithm;
        this.logger = pLogger;
    }

    @Override
    public AlgorithmStatus run(ReachedSet reachedSet) throws CPAException, InterruptedException, CPAEnabledAnalysisPropertyViolationException {
        logger.log(Level.INFO, "Running legion algorithm");
        // logger.log(Level.INFO, this.algorithm.getClass());


        Set<AbstractState> collection_empty = reachedSet.asCollection();
        // System.out.println(collection_empty.size());

        algorithm.run(reachedSet);
        // Set<AbstractState> collection = reachedSet.asCollection();
        // System.out.println(collection.size());
        // Iterator<AbstractState> i = collection.iterator();
        // AbstractState state = i.next();
        // System.out.println(state.toString());
        // AbstractState state2 = i.next();
        // System.out.println(state2.toString());
        
        // algorithm.run(reachedSet);
        // Set<AbstractState> collection2 = reachedSet.asCollection();
        // System.out.println(collection2.size());
        // Iterator<AbstractState> i2 = collection2.iterator();
        // AbstractState state3 = i2.next();
        // System.out.println(state3.toString());
        // AbstractState state4 = i2.next();
        // System.out.println(state4.toString());

        return AlgorithmStatus.NO_PROPERTY_CHECKED;
    }
}
