/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2017  Dirk Beyer
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
package org.sosy_lab.cpachecker.cpa.interval;

import org.sosy_lab.cpachecker.cfa.blocks.Block;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.cfa.model.FunctionExitNode;
import org.sosy_lab.cpachecker.core.defaults.GenericReducer;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.util.Pair;
import org.sosy_lab.cpachecker.util.states.MemoryLocation;

class IntervalAnalysisReducer extends GenericReducer<UnifyAnalysisState, Precision> {

    @Override
    protected UnifyAnalysisState getVariableReducedState0(UnifyAnalysisState pExpandedState, Block pContext,
            CFANode pCallNode) {
        UnifyAnalysisState clonedElement = pExpandedState;
        for (MemoryLocation trackedVar : pExpandedState.getIntervalMap().keySet()) {
            // ignore offset (like "3" from "array[3]") to match assignments in
            // loops ("array[i]=12;")
            if (!pContext.getVariables().contains(trackedVar.getIdentifier())) {
                clonedElement = clonedElement.removeInterval(trackedVar.getIdentifier());
            }
        }
        return clonedElement;
    }

    @Override
    protected UnifyAnalysisState getVariableExpandedState0(UnifyAnalysisState pRootState, Block pReducedContext,
            UnifyAnalysisState pReducedState) {
        // the expanded state will contain:
        // - all variables of the reduced state -> copy the state
        // - all non-block variables of the rootState -> copy those values
        // - not the variables of rootState used in the block -> just ignore
        // those values
        UnifyAnalysisState diffElement = pReducedState;

        for (MemoryLocation trackedVar : pRootState.getIntervalMap().keySet()) {
            // ignore offset ("3" from "array[3]") to match assignments in loops
            // ("array[i]=12;")
            if (!pReducedContext.getVariables().contains(trackedVar.getIdentifier())) {
                diffElement = diffElement.assignElement(trackedVar.getIdentifier(), pRootState.getInterval(trackedVar.getIdentifier()));

                // } else {
                // ignore this case, the variables are part of the reduced state
                // (or might even be deleted, then they must stay unknown)
            }
        }

        return diffElement;
    }

    @Override
    protected Precision getVariableReducedPrecision0(Precision pPrecision, Block pContext) {

        // TODO: anything meaningful we can do here?

        return pPrecision;
    }

    @Override
    protected Precision getVariableExpandedPrecision0(Precision pRootPrecision, Block pRootContext,
            Precision pReducedPrecision) {

        // TODO: anything meaningful we can do here?

        return pReducedPrecision;
    }

    @Override
    protected Object getHashCodeForState0(UnifyAnalysisState pElementKey, Precision pPrecisionKey) {
        return Pair.of(pElementKey.getIntervalMap(), pPrecisionKey);
    }

    @Override
    protected UnifyAnalysisState rebuildStateAfterFunctionCall0(UnifyAnalysisState pRootState,
            UnifyAnalysisState entryState, UnifyAnalysisState pExpandedState, FunctionExitNode exitLocation) {

        UnifyAnalysisState rootState = pRootState;
        UnifyAnalysisState expandedState = pExpandedState;

        return expandedState.rebuildStateAfterFunctionCall(rootState, exitLocation);
    }
}
