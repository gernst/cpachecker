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
package org.sosy_lab.cpachecker.util.ltl.formulas;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

public abstract class PropositionalFormula implements LtlFormula {

  public ImmutableList<? extends LtlFormula> children;

  PropositionalFormula(Iterable<? extends LtlFormula> children) {
    ImmutableList<? extends LtlFormula> list = ImmutableList.copyOf(children);
    this.children = list;
  }

  PropositionalFormula(LtlFormula... children) {
    ImmutableList<? extends LtlFormula> list = ImmutableList.copyOf(children);
    this.children = list;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    return prime + children.hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    PropositionalFormula other = (PropositionalFormula) obj;
    return children.equals(other.children);
  }

  protected abstract String getSymbol();

  @Override
  public String toString() {
    String delimiter = String.valueOf(getSymbol());
    return "("
        + String.join(" " + delimiter + " ", Iterables.transform(children, Object::toString))
        + ")";
  }
}
