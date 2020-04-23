/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2018  Dirk Beyer
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
package org.sosy_lab.cpachecker.cpa.value;

import java.util.Random;
import java.util.logging.Level;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.sosy_lab.cpachecker.cfa.types.c.CBasicType;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.types.Type;
import org.sosy_lab.cpachecker.cfa.types.c.CSimpleType;
import org.sosy_lab.cpachecker.cpa.value.type.NumericValue;
import org.sosy_lab.cpachecker.util.states.MemoryLocation;
import org.sosy_lab.cpachecker.util.states.MemoryLocationValueHandler;

/**
 * Memory location value handler that assigns randomly chosen values to the given
 * memory locations.
 */
public final class RandomValueAssigner implements MemoryLocationValueHandler {

  private Random rnd;
  private final LogManager logger;

  public RandomValueAssigner(LogManager logger, long seed){
    this.logger = logger;
    this.rnd = new Random(seed);
  }

  public RandomValueAssigner(LogManager logger){
    this.logger = logger;
    this.rnd = new Random();
  }

  /**
   * Assign a random value to the {@link MemoryLocation} from the given {@link ValueAnalysisState}.
   *
   * @param pMemLocation the memory location to remove
   * @param pType the type of the memory location that should be removed
   * @param pPreviousState the {@link org.sosy_lab.cpachecker.cpa.value.ValueAnalysisState} which preceedes
   *    pState. This state will be marked when a random value is assigned in pState.
   * @param pState the {@link org.sosy_lab.cpachecker.cpa.value.ValueAnalysisState} to use.
   *    Value assignments will happen in this state
   * @param pValueVisitor unused, may be null
   */
  @Override
  public void handle(
      MemoryLocation pMemLocation,
      Type pType,
      ValueAnalysisState pPreviousState,
      ValueAnalysisState pState,
      @Nullable ExpressionValueVisitor pValueVisitor) {

    if (!(pType instanceof CSimpleType)){
        throw new IllegalArgumentException("Unknown value was not of simple c type.");
    }

    CBasicType basicType = (( CSimpleType ) pType).getType();

    switch (basicType) {
        case INT:
            NumericValue value = new NumericValue(this.rnd.nextInt());
            this.logger.log(Level.INFO, "Assigned random value " + value.toString());
            pState.assignConstant(pMemLocation, value, pType);
            pPreviousState.nonDeterministicMark = true;
            break;
        default:
            throw new IllegalArgumentException("Unknown values of c type " + basicType.toASTString());
    }
    
  }
}