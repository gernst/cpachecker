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
package org.sosy_lab.cpachecker.cpa.smg;

import com.google.common.collect.BiMap;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import javax.annotation.Nullable;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionDeclaration;
import org.sosy_lab.cpachecker.core.defaults.LatticeAbstractState;
import org.sosy_lab.cpachecker.cpa.smg.SMGIntersectStates.SMGIntersectionResult;
import org.sosy_lab.cpachecker.cpa.smg.evaluator.SMGAbstractObjectAndState.SMGAddressValueAndState;
import org.sosy_lab.cpachecker.cpa.smg.graphs.CLangSMG;
import org.sosy_lab.cpachecker.cpa.smg.graphs.PredRelation;
import org.sosy_lab.cpachecker.cpa.smg.graphs.UnmodifiableCLangSMG;
import org.sosy_lab.cpachecker.cpa.smg.graphs.edge.SMGEdgeHasValue;
import org.sosy_lab.cpachecker.cpa.smg.graphs.edge.SMGEdgeHasValueFilter;
import org.sosy_lab.cpachecker.cpa.smg.graphs.edge.SMGEdgePointsTo;
import org.sosy_lab.cpachecker.cpa.smg.graphs.object.SMGObject;
import org.sosy_lab.cpachecker.cpa.smg.graphs.object.SMGRegion;
import org.sosy_lab.cpachecker.cpa.smg.graphs.value.SMGExplicitValue;
import org.sosy_lab.cpachecker.cpa.smg.graphs.value.SMGKnownExpValue;
import org.sosy_lab.cpachecker.cpa.smg.graphs.value.SMGKnownSymValue;
import org.sosy_lab.cpachecker.cpa.smg.graphs.value.SMGSymbolicValue;
import org.sosy_lab.cpachecker.cpa.smg.join.SMGJoinStatus;
import org.sosy_lab.cpachecker.cpa.smg.refiner.SMGInterpolant;
import org.sosy_lab.cpachecker.cpa.smg.refiner.SMGMemoryPath;
import org.sosy_lab.cpachecker.util.states.MemoryLocation;

/**
 * A view on a CLangSMG, where no modifications are allowed.
 *
 * <p>All returned Collections are unmodifiable.
 */
public interface UnmodifiableSMGState extends LatticeAbstractState<UnmodifiableSMGState> {

  /**
   * Returns mutable instance of subclass. Changes to the returned instance are independent of this
   * immutable instance and do not change it.
   */
  SMGState copyOf();

  /**
   * Returns mutable instance of subclass, with the given SMG and ExplicitValues. Changes to the
   * returned instance are independent of this immutable instance and do not change it.
   */
  SMGState copyWith(CLangSMG pSmg, BiMap<SMGKnownSymValue, SMGKnownExpValue> pValues);

  int getId();

  int getPredecessorId();

  UnmodifiableCLangSMG getHeap();

  void performConsistencyCheck(SMGRuntimeCheck pLevel) throws SMGInconsistentException;

  String toDot(String pName, String pLocation);

  String getErrorDescription();

  String getNoteMessageOnElement(Object elem);

  UnmodifiableSMGState withViolationsOf(SMGState pOther);

  SMGObject getFunctionReturnObject();

  public SMGObject getObjectForVisibleVariable(String pVariableName);

  List<SMGAddressValueAndState> getPointerFromValue(Integer pValue) throws SMGInconsistentException;

  boolean isPointer(Integer pValue);

  boolean isObjectExternallyAllocated(SMGObject pObject);

  boolean isObjectValid(SMGObject pObject);

  boolean isGlobal(String variable);

  boolean isGlobal(SMGObject object);

  boolean isHeapObject(SMGObject object);

  boolean isBlockEnded();

  boolean containsValue(int value);

  Set<Entry<SMGKnownSymValue, SMGKnownExpValue>> getExplicitValues();

  UnmodifiableSMGState setInvalidRead();

  UnmodifiableSMGState setInvalidWrite();

  @Override
  UnmodifiableSMGState join(UnmodifiableSMGState reachedState) throws SMGInconsistentException;

  @Override
  boolean isLessOrEqual(UnmodifiableSMGState reachedState) throws SMGInconsistentException;

  @Nullable
  Integer getAddress(SMGRegion memory, long offset);

  @Nullable
  public Integer getAddress(SMGObject memory, long offset, SMGTargetSpecifier tg);

  boolean containsInvalidElement(Object elem);

  Collection<Object> getInvalidChain();

  Collection<Object> getCurrentChain();

  UnmodifiableSMGState setInvalidFree();

  Set<SMGEdgeHasValue> getHVEdges(SMGEdgeHasValueFilter pFilter);

  Set<SMGEdgeHasValue> getHVEdges();

  UnmodifiableSMGState setUnknownDereference();

  boolean isTrackPredicatesEnabled();

  PredRelation getPathPredicateRelation();

  PredRelation getErrorPredicateRelation();

  boolean isExplicit(SMGKnownSymValue value);

  SMGExplicitValue getExplicit(SMGKnownSymValue pKey);

  boolean hasMemoryErrors();

  boolean hasMemoryLeaks();

  boolean isInNeq(SMGSymbolicValue pValue1, SMGSymbolicValue pValue2);

  SMGObject getObjectForFunction(CFunctionDeclaration pDeclaration);

  SMGJoinStatus valueIsLessOrEqual(
      SMGKnownSymValue value1, SMGKnownSymValue value2, UnmodifiableSMGState smgState2);

  SMGEdgePointsTo getPointsToEdge(int pSymbolicValue);

  int sizeOfHveEdges();

  Set<SMGMemoryPath> getMemoryPaths();

  SMGInterpolant createInterpolant(Set<SMGAbstractionBlock> pAbstractionBlocks);

  SMGInterpolant createInterpolant();

  SMGIntersectionResult intersectStates(UnmodifiableSMGState pOtherState);

  Map<SMGObject, SMGMemoryPath> getHeapObjectMemoryPaths();

  Map<MemoryLocation, SMGRegion> getStackVariables();

  CLangStackFrame getStackFrame();
}