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
package org.sosy_lab.cpachecker.cpa.value;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.sosy_lab.common.collect.PathCopyingPersistentTreeMap;
import org.sosy_lab.common.collect.PersistentMap;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.cpachecker.cfa.types.c.CNumericTypes;
import org.sosy_lab.cpachecker.core.interfaces.AbstractQueryableState;
import org.sosy_lab.cpachecker.core.interfaces.AbstractStateWithTargetVariable;
import org.sosy_lab.cpachecker.core.interfaces.FormulaReportingState;
import org.sosy_lab.cpachecker.core.interfaces.TargetableWithPredicatedAnalysis;
import org.sosy_lab.cpachecker.exceptions.InvalidQueryException;
import org.sosy_lab.cpachecker.util.predicates.interfaces.BooleanFormula;
import org.sosy_lab.cpachecker.util.predicates.interfaces.BooleanFormulaManager;
import org.sosy_lab.cpachecker.util.predicates.interfaces.NumeralFormula;
import org.sosy_lab.cpachecker.util.predicates.interfaces.NumeralFormula.RationalFormula;
import org.sosy_lab.cpachecker.util.predicates.interfaces.NumeralFormulaManager;
import org.sosy_lab.cpachecker.util.predicates.interfaces.view.FormulaManagerView;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import com.google.common.primitives.Longs;

public class ValueAnalysisState implements AbstractQueryableState, FormulaReportingState, Serializable,
    TargetableWithPredicatedAnalysis, AbstractStateWithTargetVariable {

  private static final long serialVersionUID = -3152134511524554357L;

  private static final Set<MemoryLocation> blacklist = new HashSet<>();

  static void addToBlacklist(MemoryLocation var) {
    blacklist.add(checkNotNull(var));
  }

  private static ValueAnalysisTargetChecker checker = null;

  static void initChecker(Configuration pConfig) throws InvalidConfigurationException{
    checker = new ValueAnalysisTargetChecker(pConfig);
  }

  /**
   * the map that keeps the name of variables and their constant values
   */
  private PersistentMap<MemoryLocation, Value> constantsMap;

  /**
   * the current delta of this state to the previous state
   */
  private Set<MemoryLocation> delta;

  public ValueAnalysisState() {
    constantsMap = PathCopyingPersistentTreeMap.of();
  }

  public ValueAnalysisState(PersistentMap<MemoryLocation, Value> pConstantsMap) {
    this.constantsMap = pConstantsMap;
  }

  /**
   * This method assigns a value to the variable and puts it in the map.
   *
   * @param variableName name of the variable.
   * @param value value to be assigned.
   */
  void assignConstant(String variableName, Value value) {
    if (blacklist.contains(MemoryLocation.valueOf(variableName))) {
      return;
    }
    constantsMap = constantsMap.putAndCopy(
        MemoryLocation.valueOf(variableName), checkNotNull(value));
  }

  /**
   * This method assigns a value to the variable and puts it in the map.
   *
   * @param pMemoryLocation the location in the memory.
   * @param value value to be assigned.
   */
  public void assignConstant(MemoryLocation pMemoryLocation, Value value) {
    if (blacklist.contains(pMemoryLocation)) {
      return;
    }
    constantsMap = constantsMap.putAndCopy(
        pMemoryLocation, checkNotNull(value));
  }

  /**
   * This method removes a variable from the underlying map and returns the removed value.
   *
   * @param variableName the name of the variable to remove
   * @return the value of the removed variable
   */
  public Value forget(String variableName) {
    return forget(MemoryLocation.valueOf(variableName));
  }

  /**
   * This method removes a memory location from the underlying map and returns the removed value.
   *
   * @param variableName the name of the memory location to remove
   * @return the value of the removed memory location
   */
  public Value forget(MemoryLocation pMemoryLocation) {
    Value value = constantsMap.get(pMemoryLocation);
    constantsMap = constantsMap.removeAndCopy(pMemoryLocation);

    return value;
  }

  /**
   * This method removes all variables and their respective values from the underlying map.
   *
   * @param variableNames the names of the variables to be removed
   */
  void removeAll(Collection<String> variableNames) {
    for (String variableName : variableNames) {
      constantsMap = constantsMap.removeAndCopy(MemoryLocation.valueOf(variableName));
    }
  }

  /**
   * This method drops all entries belonging to the stack frame of a function. This method should be called right before leaving a function.
   *
   * @param functionName the name of the function that is about to be left
   */
  void dropFrame(String functionName) {
    for (MemoryLocation variableName : constantsMap.keySet()) {
      if (variableName.isOnFunctionStack(functionName)) {
        constantsMap = constantsMap.removeAndCopy(variableName);
      }
    }
  }

  /**
   * This method returns the value for the given variable.
   *
   * @param variableName the name of the variable for which to get the value
   * @throws NullPointerException - if no value is present in this state for the given variable
   * @return the value associated with the given variable
   */
  public Value getValueFor(String variableName) {
    return getValueFor(MemoryLocation.valueOf(variableName));
  }

  /**
   * This method returns the value for the given variable.
   *
   * @param variableName the name of the variable for which to get the value
   * @throws NullPointerException - if no value is present in this state for the given variable
   * @return the value associated with the given variable
   */
  public Value getValueFor(MemoryLocation variableName) {
    return checkNotNull(constantsMap.get(variableName));
  }

  /**
   * This method checks whether or not the given variable is contained in this state.
   *
   * @param variableName the name of variable to check for
   * @return true, if the variable is contained, else false
   */
  public boolean contains(String variableName) {
    return contains(MemoryLocation.valueOf(variableName));
  }

  /**
   * This method checks whether or not the given Memory Location
   * is contained in this state.
   *
   * @param pMemoryLocation the location in the Memory to check for
   * @return true, if the variable is contained, else false
   */
  public boolean contains(MemoryLocation pMemoryLocation) {
    return constantsMap.containsKey(pMemoryLocation);
  }

  /**
   * This method determines the total number of variables contained in this state.
   *
   * @return the total number of variables contained in this state
   */
  public int getSize() {
    return constantsMap.size();
  }

  /**
   * This method determines the number of global variables contained in this state.
   *
   * @return the number of global variables contained in this state
   */
  int getNumberOfGlobalVariables() {
    int numberOfGlobalVariables = 0;

    for (MemoryLocation variableName : constantsMap.keySet()) {
      if (!variableName.isOnFunctionStack()) {
        numberOfGlobalVariables++;
      }
    }

    return numberOfGlobalVariables;
  }

  /**
   * This element joins this element with another element.
   *
   * @param reachedState the other element to join with this element
   * @return a new state representing the join of this element and the other element
   */
  ValueAnalysisState join(ValueAnalysisState reachedState) {
    PersistentMap<MemoryLocation, Value> newConstantsMap = PathCopyingPersistentTreeMap.of();

    for (Map.Entry<MemoryLocation, Value> otherEntry : reachedState.constantsMap.entrySet()) {
      MemoryLocation key = otherEntry.getKey();

      if (Objects.equals(otherEntry.getValue(), constantsMap.get(key))) {
        newConstantsMap = newConstantsMap.putAndCopy(key, otherEntry.getValue());
      }
    }

    // return the reached state if both maps are equal
    if (newConstantsMap.size() == reachedState.constantsMap.size()) {
      return reachedState;
    } else {
      return new ValueAnalysisState(newConstantsMap);
    }
  }

  /**
   * This method decides if this element is less or equal than the other element, based on the order imposed by the lattice.
   *
   * @param other the other element
   * @return true, if this element is less or equal than the other element, based on the order imposed by the lattice
   */
  boolean isLessOrEqual(ValueAnalysisState other) {

    // also, this element is not less or equal than the other element, if it contains less elements
    if (constantsMap.size() < other.constantsMap.size()) {
      return false;
    }

    // also, this element is not less or equal than the other element,
    // if any one constant's value of the other element differs from the constant's value in this element
    for (Map.Entry<MemoryLocation, Value> otherEntry : other.constantsMap.entrySet()) {
      MemoryLocation key = otherEntry.getKey();

      if (!otherEntry.getValue().equals(constantsMap.get(key))) {
        return false;
      }
    }

    return true;
  }

  @Override
  public ValueAnalysisState clone() {
    return new ValueAnalysisState(PathCopyingPersistentTreeMap.copyOf(constantsMap));
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }

    if (other == null) {
      return false;
    }

    if (!getClass().equals(other.getClass())) {
      return false;
    }

    ValueAnalysisState otherElement = (ValueAnalysisState) other;

    return otherElement.constantsMap.equals(constantsMap);
  }

  @Override
  public int hashCode() {
    return constantsMap.hashCode();
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("[");
    for (Map.Entry<MemoryLocation, Value> entry : constantsMap.entrySet()) {
      MemoryLocation key = entry.getKey();
      sb.append(" <");
      sb.append(key.getAsSimpleString());
      sb.append(" = ");
      sb.append(entry.getValue());
      sb.append(">\n");
    }

    return sb.append("] size->  ").append(constantsMap.size()).toString();
  }

  /**
   * This method returns a more compact string representation of the state, compared to toString().
   *
   * @return a more compact string representation of the state
   */
  public String toCompactString() {
    StringBuilder sb = new StringBuilder();

    sb.append("[");
    Joiner.on(", ").withKeyValueSeparator("=").appendTo(sb, constantsMap);
    sb.append("]");

    return sb.toString();
  }

  @Override
  public Object evaluateProperty(String pProperty) throws InvalidQueryException {
    pProperty = pProperty.trim();

    if (pProperty.startsWith("contains(")) {
      String varName = pProperty.substring("contains(".length(), pProperty.length() - 1);
      return this.constantsMap.containsKey(MemoryLocation.valueOf(varName));
    } else {
      String[] parts = pProperty.split("==");
      if (parts.length != 2) {
        Value value = this.constantsMap.get(MemoryLocation.valueOf(pProperty));
        if (value.isExplicitlyKnown()) {
          return value;
        } else {
          throw new InvalidQueryException("The Query \"" + pProperty + "\" is invalid. Could not find the variable \""
              + pProperty + "\"");
        }
      } else {
        return checkProperty(pProperty);
      }
    }
  }

  @Override
  public boolean checkProperty(String pProperty) throws InvalidQueryException {
    // e.g. "x==5" where x is a variable. Returns if 5 is the associated constant
    String[] parts = pProperty.split("==");

    if (parts.length != 2) {
      throw new InvalidQueryException("The Query \"" + pProperty
          + "\" is invalid. Could not split the property string correctly.");
    } else {
      // The following is a hack
      Long value = this.constantsMap.get(MemoryLocation.valueOf(parts[0])).asLong(CNumericTypes.INT);

      if (value == null) {
        return false;
      } else {
        try {
          return value == Long.parseLong(parts[1]);
        } catch (NumberFormatException e) {
          // The command might contains something like "main::p==cmd" where the user wants to compare the variable p to the variable cmd (nearest in scope)
          // perhaps we should omit the "main::" and find the variable via static scoping ("main::p" is also not intuitive for a user)
          // TODO: implement Variable finding via static scoping
          throw new InvalidQueryException("The Query \"" + pProperty + "\" is invalid. Could not parse the long \""
              + parts[1] + "\"");
        }
      }
    }
  }

  @Override
  public void modifyProperty(String pModification) throws InvalidQueryException {
    Preconditions.checkNotNull(pModification);

    // either "deletevalues(methodname::varname)" or "setvalue(methodname::varname:=1929)"
    String[] statements = pModification.split(";");
    for (int i = 0; i < statements.length; i++) {
      String statement = statements[i].trim().toLowerCase();
      if (statement.startsWith("deletevalues(")) {
        if (!statement.endsWith(")")) {
          throw new InvalidQueryException(statement + " should end with \")\"");
        }

        String varName = statement.substring("deletevalues(".length(), statement.length() - 1);

        if (contains(varName)) {
          forget(varName);
        } else {
          // varname was not present in one of the maps
          // i would like to log an error here, but no logger is available
        }
      }

      else if (statement.startsWith("setvalue(")) {
        if (!statement.endsWith(")")) {
          throw new InvalidQueryException(statement + " should end with \")\"");
        }

        String assignment = statement.substring("setvalue(".length(), statement.length() - 1);
        String[] assignmentParts = assignment.split(":=");

        if (assignmentParts.length != 2) {
          throw new InvalidQueryException("The Query \"" + pModification
              + "\" is invalid. Could not split the property string correctly.");
        } else {
          String varName = assignmentParts[0].trim();
          try {
            Value newValue = new NumericValue(Long.parseLong(assignmentParts[1].trim()));
            this.assignConstant(varName, newValue);
          } catch (NumberFormatException e) {
            throw new InvalidQueryException("The Query \"" + pModification
                + "\" is invalid. Could not parse the long \"" + assignmentParts[1].trim() + "\"");
          }
        }
      }
    }
  }

  @Override
  public String getCPAName() {
    return "ValueAnalysis";
  }

  @Override
  public BooleanFormula getFormulaApproximation(FormulaManagerView manager) {
    BooleanFormulaManager bfmgr = manager.getBooleanFormulaManager();
    NumeralFormulaManager<NumeralFormula, RationalFormula> nfmgr = manager.getRationalFormulaManager();
    BooleanFormula formula = bfmgr.makeBoolean(true);

    for (Map.Entry<MemoryLocation, Value> entry : constantsMap.entrySet()) {
      RationalFormula var = nfmgr.makeVariable(entry.getKey().getAsSimpleString());
      // TODO explicitfloat: handle the case that it's not a long
      // The following is a hack
      RationalFormula val = nfmgr.makeNumber(entry.getValue().asLong(CNumericTypes.INT));
      formula = bfmgr.and(formula, nfmgr.equal(var, val));
    }

    return formula;
  }

  /**
   * This method determines the set of variable names that are in the other state but not in this,
   * or that are in both, but differ in their value.
   *
   * @param other the other state for which to get the difference
   * @return the set of variable names that differ
   */
  public Set<MemoryLocation> getDifference(ValueAnalysisState other) {
    Set<MemoryLocation> difference = new HashSet<>();

    for (MemoryLocation variableName : other.constantsMap.keySet()) {
      if (!contains(variableName)) {
        difference.add(variableName);
      }

      else if (!getValueFor(variableName).equals(other.getValueFor(variableName))) {
        difference.add(variableName);
      }
    }

    return difference;
  }

  /**
   * This method returns the current delta of this state.
   *
   * @return the current delta of this state
   */
  Collection<MemoryLocation> getDelta() {
    return ImmutableSet.copyOf(delta);
  }

  /**
   * This method sets the delta of this state, in relation to the given other state.
   *
   * This is used for a more efficient abstraction computation, where only the delta of a state is considered.
   *
   * @param other the state to which to compute the delta
   */
  void addToDelta(ValueAnalysisState other) {
    delta = other.getDifference(this);
    if (other.delta != null) {
      delta.addAll(other.delta);
    }
  }

  /**
   * This method resets the delta of this state.
   */
  void clearDelta() {
    delta = null;
  }

  /**
   * This method adds the key-value-pairs of this state to the given value mapping and returns the new mapping.
   *
   * @param valueMapping the mapping from variable name to the set of values of this variable
   * @return the new mapping
   */
  public Multimap<String, Value> addToValueMapping(Multimap<String, Value> valueMapping) {
    for (Map.Entry<MemoryLocation, Value> entry : constantsMap.entrySet()) {
      valueMapping.put(entry.getKey().getAsSimpleString(), entry.getValue());
    }

    return valueMapping;
  }

  /**
   * This method returns the set of tracked variables by this state.
   *
   * @return the set of tracked variables by this state
   */
  public Set<String> getTrackedVariableNames() {
    Set<String> result = new HashSet<>();

    for (MemoryLocation loc : constantsMap.keySet()) {
      result.add(loc.getAsSimpleString());
    }

    // no copy necessary, fresh instance of set
    return Collections.unmodifiableSet(result);
  }

  /**
   * This method returns the set of tracked variables by this state.
   *
   * @return the set of tracked variables by this state
   */
  public Set<MemoryLocation> getTrackedMemoryLocations() {
    // no copy necessary, set is immutable
    return constantsMap.keySet();
  }

  /**
   * This method returns the internal mapping of this state.
   *
   * @return the internal mapping of this state
   * @TODO: eliminate this - breaks encapsulation
   */
  Map<MemoryLocation, Value> getConstantsMap() {
    //TODO Investigate if this API change breaks functionality
    return constantsMap;
  }

  public Map<MemoryLocation, Value> getConstantsMapView() {
    return Collections.unmodifiableMap(constantsMap);
  }

  public static class MemoryLocation implements Comparable<MemoryLocation>, Serializable {

    private static final long serialVersionUID = -8910967707373729034L;
    private final String functionName;
    private final String identifier;
    private final long offset;

    private MemoryLocation(String pFunctionName, String pIdentifier,
        long pOffset) {
      checkNotNull(pFunctionName);
      checkNotNull(pIdentifier);

      functionName = pFunctionName;
      identifier = pIdentifier;
      offset = pOffset;
    }

    private MemoryLocation(String pIdentifier, long pOffset) {
      checkNotNull(pIdentifier);

      functionName = null;
      identifier = pIdentifier;
      offset = pOffset;
    }

    public static MemoryLocation valueOf(String pFunctionName,
        String pIdentifier, long pOffest) {
      return new MemoryLocation(pFunctionName, pIdentifier, pOffest);
    }

    @Override
    public boolean equals(Object other) {

      if (this == other) {
        return true;
      }

      if (!(other instanceof MemoryLocation)) {
        return false;
      }

      MemoryLocation otherLocation = (MemoryLocation) other;

      return Objects.equals(functionName, otherLocation.functionName)
          && Objects.equals(identifier, otherLocation.identifier)
          && offset == otherLocation.offset;
    }

    @Override
    public int hashCode() {

      int hc = 17;
      int hashMultiplier = 59;

      hc = hc * hashMultiplier + Objects.hashCode(functionName);
      hc = hc * hashMultiplier + identifier.hashCode();
      hc = hc * hashMultiplier + Longs.hashCode(offset);

      return hc;
    }

    public static MemoryLocation valueOf(String pIdentifier, long pOffest) {
      return new MemoryLocation(pIdentifier, pOffest);
    }

    public static MemoryLocation valueOf(String pVariableName) {

      String[] nameParts    = pVariableName.split("::");
      String[] offsetParts  = pVariableName.split("/");

      boolean isScoped  = nameParts.length == 2;
      boolean hasOffset = offsetParts.length == 2;

      int offset = hasOffset ? Integer.parseInt(offsetParts[1]) : 0;

      if(isScoped) {
        return new MemoryLocation(nameParts[0], nameParts[1].replace("/" + offset, ""), offset);
      }

      else {
        return new MemoryLocation(nameParts[0].replace("/" + offset, ""), offset);
      }
    }

    public String getAsSimpleString() {
      /*
            String simpleName = identifier + "[" + offset + "]";

      return isOnFunctionStack() ? (functionName + "::" + simpleName) : simpleName;
      */

      return isOnFunctionStack() ? (functionName + "::" + identifier) : (identifier);
    }

    public String serialize() {
      String simpleName = identifier + "/" + offset;

      return isOnFunctionStack() ? (functionName + "::" + simpleName) : simpleName;
    }

    public boolean isOnFunctionStack() {
      return functionName != null;
    }

    public boolean isOnFunctionStack(String pFunctionName) {
      return functionName != null && pFunctionName.equals(functionName);
    }

    public String getFunctionName() {
      return checkNotNull(functionName);
    }

    public String getIdentifier() {
      return identifier;
    }

    public long getOffset() {
      return offset;
    }

    @Override
    public String toString() {
      return getAsSimpleString();
    }

    public static PersistentMap<MemoryLocation, Long> transform(
        PersistentMap<String, Long> pConstantMap) {

      PersistentMap<MemoryLocation, Long> result = PathCopyingPersistentTreeMap.of();

      for (Map.Entry<String, Long> entry : pConstantMap.entrySet()) {
        result = result.putAndCopy(valueOf(entry.getKey()), checkNotNull(entry.getValue()));
      }

      return result;
    }

    @Override
    public int compareTo(MemoryLocation other) {

      int result = 0;

      if (isOnFunctionStack()) {
        if (other.isOnFunctionStack()) {
          result = functionName.compareTo(other.functionName);
        } else {
          result = 1;
        }
      } else {
        if (other.isOnFunctionStack()) {
          result = -1;
        } else {
          result = 0;
        }
      }

      if (result != 0) {
        return result;
      }

      return ComparisonChain.start()
          .compare(identifier, other.identifier)
          .compare(offset, other.offset)
          .result();
    }
  }


  public Set<MemoryLocation> getMemoryLocationsOnStack(String pFunctionName) {
    Set<MemoryLocation> result = new HashSet<>();

    Set<MemoryLocation> memoryLocations = constantsMap.keySet();

    for (MemoryLocation memoryLocation : memoryLocations) {
      if (memoryLocation.isOnFunctionStack() && memoryLocation.getFunctionName().equals(pFunctionName)) {
        result.add(memoryLocation);
      }
    }

    // Doesn't need a copy, Memory Location is Immutable
    return Collections.unmodifiableSet(result);
  }

  public Set<MemoryLocation> getGlobalMemoryLocations() {
    Set<MemoryLocation> result = new HashSet<>();

    Set<MemoryLocation> memoryLocations = constantsMap.keySet();

    for (MemoryLocation memoryLocation : memoryLocations) {
      if (!memoryLocation.isOnFunctionStack()) {
        result.add(memoryLocation);
      }
    }

    // Doesn't need a copy, Memory Location is Immutable
    return Collections.unmodifiableSet(result);
  }

  public void forgetValuesWithIdentifier(String pIdentifier) {
    for (MemoryLocation memoryLocation : constantsMap.keySet()) {
      if (memoryLocation.getIdentifier().equals(pIdentifier)) {
        constantsMap = constantsMap.removeAndCopy(memoryLocation);
      }
    }
  }

  @Override
  public boolean isTarget() {
    return checker != null && checker.isTarget(constantsMap);
  }

  @Override
  public ViolatedProperty getViolatedProperty() throws IllegalStateException {
    if(isTarget()){
      return ViolatedProperty.OTHER;
    }
    return null;
  }

  @Override
  public BooleanFormula getErrorCondition(FormulaManagerView pFmgr) {
    return checker==null? pFmgr.getBooleanFormulaManager().makeBoolean(false):checker.getErrorCondition(pFmgr);
  }

  @Override
  public String getTargetVariableName() {
    return checker== null?"":checker.getTargetVariableName();
  }

  /** If there was a recursive function, we have wrong values for scoped variables in the returnState.
   * This function rebuilds a new state with the correct values from the previous callState.
   * We delete the wrong values and insert new values, if necessary. */
  public ValueAnalysisState rebuildStateAfterFunctionCall(final ValueAnalysisState callState) {

    // we build a new state from:
    // - local variables from callState,
    // - global variables from THIS,
    // - the local return variable from THIS.
    // we copy callState and override all global values and the return variable.

    final ValueAnalysisState rebuildState = callState.clone();

    // first forget all global information
    for (final ValueAnalysisState.MemoryLocation trackedVar : callState.getTrackedMemoryLocations()) {
      if (!trackedVar.isOnFunctionStack()) { // global -> delete
        rebuildState.forget(trackedVar);
      }
    }

    // second: learn new information
    for (final ValueAnalysisState.MemoryLocation trackedVar : this.getTrackedMemoryLocations()) {

      if (!trackedVar.isOnFunctionStack()) { // global -> override deleted value
        rebuildState.assignConstant(trackedVar, this.getValueFor(trackedVar));

      } else if (ValueAnalysisTransferRelation.FUNCTION_RETURN_VAR.equals(trackedVar.getIdentifier())) {
        // lets assume, that RETURN_VAR is only tracked along one edge, which is the ReturnEdge.
        // so that we can ignore the functionname for this condition.
        assert (!rebuildState.contains(trackedVar)) :
                "calling function should not contain return-variable of called function: " + trackedVar;
        if (this.contains(trackedVar)) {
          rebuildState.assignConstant(trackedVar, this.getValueFor(trackedVar));
        }
      }
    }

    // set difference to avoid null pointer exception due to precision adaption of omniscient composite precision adjustment
    // to avoid that due to precision adaption in BAM ART which is not yet propagated tracked variable information is deleted
    rebuildState.addToDelta(rebuildState);
    return rebuildState;
  }
}