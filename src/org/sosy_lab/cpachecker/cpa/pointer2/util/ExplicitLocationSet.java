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
package org.sosy_lab.cpachecker.cpa.pointer2.util;

import java.util.Iterator;
import java.util.Set;

import com.google.common.collect.ImmutableSet;

public class ExplicitLocationSet implements LocationSet, Iterable<Location> {

  private final Set<Location> explicitSet;

  private ExplicitLocationSet(ImmutableSet<Location> pLocations) {
    assert pLocations.size() >= 1;
    this.explicitSet = pLocations;
  }

  @Override
  public boolean mayPointTo(Location pElement) {
    return this.explicitSet.contains(pElement);
  }

  @Override
  public LocationSet addElement(Location pElement) {
    if (explicitSet.contains(pElement)) {
      return this;
    }
    ImmutableSet.Builder<Location> builder = ImmutableSet.builder();
    builder.addAll(explicitSet).add(pElement);
    return new ExplicitLocationSet(builder.build());
  }

  @Override
  public LocationSet addElements(Iterable<Location> pElements) {
    ImmutableSet.Builder<Location> builder = null;
    for (Location target : pElements) {
      if (!explicitSet.contains(target)) {
        if (builder == null) {
          builder = ImmutableSet.builder();
          builder.addAll(explicitSet);
        }
        builder.add(target);
      }
    }
    if (builder == null) {
      return this;
    }
    return new ExplicitLocationSet(builder.build());
  }

  @Override
  public LocationSet removeElement(Location pElement) {
    if (!explicitSet.contains(pElement)) {
      return this;
    }
    if (getSize() == 1) {
      return LocationSetBot.INSTANCE;
    }
    ImmutableSet.Builder<Location> builder = ImmutableSet.builder();
    for (Location location : this.explicitSet) {
      if (!location.equals(pElement)) {
        builder.add(location);
      }
    }
    return new ExplicitLocationSet(builder.build());
  }

  public static LocationSet from(Location pElement) {
    return new ExplicitLocationSet(ImmutableSet.of(pElement));
  }

  public static LocationSet from(Iterable<? extends Location> pElements) {
    Iterator<? extends Location> elementIterator = pElements.iterator();
    if (!elementIterator.hasNext()) {
      return LocationSetBot.INSTANCE;
    }
    ImmutableSet.Builder<Location> builder = ImmutableSet.builder();
    while (elementIterator.hasNext()) {
      Location location = elementIterator.next();
      builder.add(location);
    }
    return new ExplicitLocationSet(builder.build());
  }

  @Override
  public boolean isBot() {
    return explicitSet.isEmpty();
  }

  @Override
  public boolean isTop() {
    return false;
  }

  @Override
  public LocationSet addElements(LocationSet pElements) {
    if (pElements == this) {
      return this;
    }
    if (pElements instanceof ExplicitLocationSet) {
      ExplicitLocationSet explicitLocationSet = (ExplicitLocationSet) pElements;
      return addElements(explicitLocationSet.explicitSet);
    }
    return pElements.addElements((LocationSet) this);
  }

  @Override
  public boolean containsAll(LocationSet pElements) {
    if (pElements == this) {
      return true;
    }
    if (pElements instanceof ExplicitLocationSet) {
      ExplicitLocationSet explicitLocationSet = (ExplicitLocationSet) pElements;
      return explicitSet.containsAll(explicitLocationSet.explicitSet);
    }
    return pElements.containsAll(this);
  }

  @Override
  public String toString() {
    return explicitSet.toString();
  }

  @Override
  public boolean equals(Object pO) {
    if (this == pO) {
      return true;
    }
    if (pO instanceof LocationSet) {
      LocationSet o = (LocationSet) pO;
      if (o.isTop()) {
        return false;
      }
      if (o.isBot()) {
        return explicitSet.isEmpty();
      }
      if (o instanceof ExplicitLocationSet) {
        ExplicitLocationSet other = (ExplicitLocationSet) o;
        return explicitSet.equals(other.explicitSet);
      }
    }
    return false;
  }

  @Override
  public int hashCode() {
    if (isBot()) {
      return LocationSetBot.INSTANCE.hashCode();
    }
    if (isTop()) {
      assert false;
      return LocationSetTop.INSTANCE.hashCode();
    }
    return explicitSet.hashCode();
  }

  @Override
  public Iterator<Location> iterator() {
    return explicitSet.iterator();
  }

  /**
   * Gets the size of the explicit location set.
   *
   * @return the size of the explicit location set.
   */
  public int getSize() {
    return explicitSet.size();
  }

}
