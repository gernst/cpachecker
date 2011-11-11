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
package org.sosy_lab.cpachecker.cpa.pointer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;

import org.sosy_lab.common.LogManager;
import org.sosy_lab.common.Pair;
import org.sosy_lab.cpachecker.cfa.ast.IASTArrayTypeSpecifier;
import org.sosy_lab.cpachecker.cfa.ast.IASTAssignment;
import org.sosy_lab.cpachecker.cfa.ast.IASTBinaryExpression;
import org.sosy_lab.cpachecker.cfa.ast.IASTCastExpression;
import org.sosy_lab.cpachecker.cfa.ast.IASTCompositeTypeSpecifier;
import org.sosy_lab.cpachecker.cfa.ast.IASTElaboratedTypeSpecifier;
import org.sosy_lab.cpachecker.cfa.ast.IASTEnumerationSpecifier;
import org.sosy_lab.cpachecker.cfa.ast.IASTExpression;
import org.sosy_lab.cpachecker.cfa.ast.IASTFieldReference;
import org.sosy_lab.cpachecker.cfa.ast.IASTFunctionCall;
import org.sosy_lab.cpachecker.cfa.ast.IASTFunctionCallAssignmentStatement;
import org.sosy_lab.cpachecker.cfa.ast.IASTFunctionCallExpression;
import org.sosy_lab.cpachecker.cfa.ast.IASTFunctionCallStatement;
import org.sosy_lab.cpachecker.cfa.ast.IASTFunctionTypeSpecifier;
import org.sosy_lab.cpachecker.cfa.ast.IASTIdExpression;
import org.sosy_lab.cpachecker.cfa.ast.IASTIntegerLiteralExpression;
import org.sosy_lab.cpachecker.cfa.ast.IASTLiteralExpression;
import org.sosy_lab.cpachecker.cfa.ast.IASTNode;
import org.sosy_lab.cpachecker.cfa.ast.IASTParameterDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.IASTPointerTypeSpecifier;
import org.sosy_lab.cpachecker.cfa.ast.IASTRightHandSide;
import org.sosy_lab.cpachecker.cfa.ast.IASTSimpleDeclSpecifier;
import org.sosy_lab.cpachecker.cfa.ast.IASTStatement;
import org.sosy_lab.cpachecker.cfa.ast.IASTStringLiteralExpression;
import org.sosy_lab.cpachecker.cfa.ast.IASTUnaryExpression;
import org.sosy_lab.cpachecker.cfa.ast.IType;
import org.sosy_lab.cpachecker.cfa.ast.StorageClass;
import org.sosy_lab.cpachecker.cfa.ast.IASTBinaryExpression.BinaryOperator;
import org.sosy_lab.cpachecker.cfa.ast.IASTUnaryExpression.UnaryOperator;
import org.sosy_lab.cpachecker.cfa.objectmodel.CFAEdge;
import org.sosy_lab.cpachecker.cfa.objectmodel.c.AssumeEdge;
import org.sosy_lab.cpachecker.cfa.objectmodel.c.CallToReturnEdge;
import org.sosy_lab.cpachecker.cfa.objectmodel.c.DeclarationEdge;
import org.sosy_lab.cpachecker.cfa.objectmodel.c.FunctionCallEdge;
import org.sosy_lab.cpachecker.cfa.objectmodel.c.FunctionDefinitionNode;
import org.sosy_lab.cpachecker.cfa.objectmodel.c.FunctionReturnEdge;
import org.sosy_lab.cpachecker.cfa.objectmodel.c.GlobalDeclarationEdge;
import org.sosy_lab.cpachecker.cfa.objectmodel.c.ReturnStatementEdge;
import org.sosy_lab.cpachecker.cfa.objectmodel.c.StatementEdge;
import org.sosy_lab.cpachecker.core.interfaces.AbstractElement;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.TransferRelation;
import org.sosy_lab.cpachecker.cpa.explicit.ExplicitElement;
import org.sosy_lab.cpachecker.cpa.pointer.Memory.InvalidPointerException;
import org.sosy_lab.cpachecker.cpa.pointer.Memory.LocalVariable;
import org.sosy_lab.cpachecker.cpa.pointer.Memory.MemoryAddress;
import org.sosy_lab.cpachecker.cpa.pointer.Memory.MemoryRegion;
import org.sosy_lab.cpachecker.cpa.pointer.Memory.PointerTarget;
import org.sosy_lab.cpachecker.cpa.pointer.Memory.StackArray;
import org.sosy_lab.cpachecker.cpa.pointer.Memory.StackArrayCell;
import org.sosy_lab.cpachecker.cpa.pointer.Memory.Variable;
import org.sosy_lab.cpachecker.cpa.pointer.Pointer.PointerOperation;
import org.sosy_lab.cpachecker.cpa.pointer.PointerElement.ElementProperty;
import org.sosy_lab.cpachecker.cpa.types.Type;
import org.sosy_lab.cpachecker.cpa.types.TypesElement;
import org.sosy_lab.cpachecker.cpa.types.Type.ArrayType;
import org.sosy_lab.cpachecker.cpa.types.Type.FunctionType;
import org.sosy_lab.cpachecker.cpa.types.Type.PointerType;
import org.sosy_lab.cpachecker.cpa.types.Type.TypeClass;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;
import org.sosy_lab.cpachecker.exceptions.UnrecognizedCCodeException;
import org.sosy_lab.cpachecker.exceptions.UnrecognizedCFAEdgeException;

public class PointerTransferRelation implements TransferRelation {

  /*
   * Exception usage during analysis:
   *
   * UnreachableStateException: Thrown when the analysis determines that the
   *      current edge represents an infeasible code path of the program. The
   *      exception will be caught silently and the new abstract element will be
   *      the bottom element of the domain.
   *
   * InvalidPointerException: The program produces a pointer related error.
   *      If it's a non-critical error like incrementing a pointer above the
   *      length of it's memory region, the exception is caught and a warning is
   *      printed. Analysis will then continue, probably with the affected
   *      pointer set to INVALID.
   *      If it's a critical error like dereferencing the pointer from above,
   *      the exception is caught a the top-most level of the analysis, an
   *      error is printed and the new abstract element will be the bottom
   *      element of the domain.
   *
   * UnrecognizedCCodeException: The program has invalid syntax, a type error or
   *      C constructs which should not appear in CIL. An error is printed and
   *      analysis will halt completely.
   *
   * ? extends RuntimeException: These exceptions should never happen during
   *      analysis as they indicate an illegal state, probably due to missing
   *      checks in the call stack of the throwing method. Program will terminate.
   */

  private static class UnreachableStateException extends Exception {

    private static final long serialVersionUID = -3075945291940304272L;

  }

  private static final String RETURN_VALUE_VARIABLE = "___cpa_temp_result_var_";

  /**
   * Here some information about the last action is stored;
   * the strengthen operator can use this to find out what information could be
   * updated.
   *
   * This information is stored in a separate object which can be garbage
   * collected after it was used, this reduces the memory footprint of a
   * PointerElement.
   */
  private static class MissingInformation {
    private Pointer         typeInformationPointer = null;
    private CFAEdge         typeInformationEdge    = null;
    private String          typeInformationName    = null;

    private Pointer         actionLeftPointer      = null;
    private Pointer         actionRightPointer     = null;
    private boolean         actionDereferenceFirst = false;
    private boolean         actionOffsetNegative   = false;
    private IASTNode        actionASTNode          = null;

    private MemoryAddress   mallocSizeMemory       = null;
    private IASTNode        mallocSizeASTNode      = null;
  }

  private MissingInformation                missing            = null;

  private static boolean                    printWarnings      = false;
  private static Set<Pair<Integer, String>> warnings           = null;
  private static LogManager                 logger             = null;
  private static LinkedList<MemoryRegion>   memoryLeakWarnings = null;

  private FunctionDefinitionNode entryFunctionDefinitionNode = null;
  private boolean entryPointProcessed = false;

  private PointerElement currentElement;
  private CFAEdge currentEdge;

  public PointerTransferRelation(boolean pPrintWarnings,
      LogManager pLogger) {
    printWarnings = pPrintWarnings;
    warnings = printWarnings ? new HashSet<Pair<Integer, String>>() : null;
    logger = pLogger;
    memoryLeakWarnings = printWarnings ? new LinkedList<MemoryRegion>() : null;
  }

  public static void addWarning(String message, CFAEdge edge, String variable) {
    if (printWarnings) {
      Integer lineNumber = null;
      if (edge != null) {
        lineNumber = edge.getLineNumber();
      }

      Pair<Integer, String> warningIndex = Pair.of(lineNumber, variable);
      if (!warnings.contains(warningIndex)) {
        warnings.add(warningIndex);
        if (lineNumber != null) {
          logger.log(Level.WARNING, "Warning: " + message + " in line "
              + lineNumber + ": " + edge.getRawStatement());
        } else {
          logger.log(Level.WARNING, "Warning: " + message);
        }
      }
    }
  }

  public static void addMemoryLeakWarning(String message, CFAEdge edge,
      MemoryRegion memoryRegion) {
    if (printWarnings) {
      Integer lineNumber = null;
      if (edge != null) {
        lineNumber = edge.getLineNumber();
      }

      MemoryRegion warningIndex = memoryRegion;
      if (!memoryLeakWarnings.contains(warningIndex)) {
        memoryLeakWarnings.add(warningIndex);
        if (lineNumber != null) {
          logger.log(Level.WARNING, "Warning: " + message + " in line "
              + lineNumber + ": " + edge.getRawStatement());
        } else {
          logger.log(Level.WARNING, "Warning: " + message);
        }
      }
    }
  }

  private static void addError(String message, CFAEdge edge) {
    if (printWarnings) {
      int lineNumber = edge.getLineNumber();
      logger.log(Level.WARNING, "ERROR: " + message + " in line " + lineNumber
          + ": " + edge.getRawStatement());
    }
  }

  @Override
  public Collection<PointerElement> getAbstractSuccessors(
      AbstractElement element, Precision precision, CFAEdge cfaEdge)
      throws CPATransferException {

    PointerElement successor = ((PointerElement)element).clone();
    if (successor.isTarget()) {
      return Collections.emptySet();
    }

    currentElement  = ((PointerElement)element).clone();
    currentEdge     = cfaEdge;

    successor.setCurrentEdge(cfaEdge);
    successor.clearProperties();

    Collection<PointerElement> successors = null;

    try
    {
      switch (cfaEdge.getEdgeType())
      {

      case DeclarationEdge:
        DeclarationEdge declEdge = (DeclarationEdge)cfaEdge;
        successors = handleDeclaration(successor, cfaEdge, declEdge.getStorageClass(), declEdge.getName(), declEdge.getDeclSpecifier());
        break;

      case StatementEdge:
        handleStatement(successor, ((StatementEdge)cfaEdge).getStatement(), (StatementEdge)cfaEdge);
        break;

      case ReturnStatementEdge:
        // this is the return-statement of a function

        // Normally, the resultPointer is there, but if we know through a type
        // information CPA that this function does not return a pointer, it's not.

        IASTExpression expression = ((ReturnStatementEdge)cfaEdge).getExpression();
        if (expression != null) {
          // non-void function
          Pointer resultPointer = successor.lookupPointer(RETURN_VALUE_VARIABLE);
          if (resultPointer != null) {
            handleAssignment(successor, RETURN_VALUE_VARIABLE, resultPointer,
                false, expression, cfaEdge);
          }
        }
        break;

      case AssumeEdge:
        AssumeEdge assumeEdge = (AssumeEdge)cfaEdge;
        handleAssume(successor, assumeEdge.getExpression(),
                assumeEdge.getTruthAssumption(), assumeEdge);
        break;

      case FunctionCallEdge:
        handleFunctionCall(successor, cfaEdge);
        break;

      case FunctionReturnEdge:
        // now handle the complete a = func(x) statement in the CallToReturnEdge
        FunctionReturnEdge returnEdge = (FunctionReturnEdge)cfaEdge;
        CallToReturnEdge ctrEdge = returnEdge.getSuccessor().getEnteringSummaryEdge();
        handleReturnFromFunction(successor, ctrEdge.getExpression(), ctrEdge);
        break;

      case BlankEdge:

        if(isEdgeToEntryPoint(cfaEdge))
        {
          // since by this point all global variables have been processed, we can now process the entry function
          // by first creating its context...
          successor.setScope(entryFunctionDefinitionNode.getFunctionName());

          List<IASTParameterDeclaration> parameters = entryFunctionDefinitionNode.getFunctionParameters();

          // ... then adding all parameters as local variables
          for(IASTParameterDeclaration declaration : parameters)
            successors = handleDeclaration(successor, cfaEdge, StorageClass.AUTO, declaration.getName(), declaration.getDeclSpecifier());

          entryPointProcessed = true;
        }
        break;

      default:
        throw new UnrecognizedCFAEdgeException(cfaEdge);
      }

    } catch (InvalidPointerException e) {
      addError(e.getMessage(), cfaEdge);
      // removed because the cpas should not declare errors any more
      //successor.setError(true);

      //assert that at least one flag is set
      if (successor.getProperties().isEmpty()) {
        logger.log(Level.WARNING, "InvalidPointerException thrown but no Flag set");
      }
      return Collections.singleton(successor);

    } catch (UnreachableStateException e) {
      return Collections.emptySet();
    }

    Collection<MemoryRegion> lostRegions = successor.checkMemoryLeak();
    if (!lostRegions.isEmpty()) {
      for (MemoryRegion lostRegion : lostRegions) {
        addMemoryLeakWarning("Memory leak: " + lostRegion
            + " is not freed and has no known pointer towards it", cfaEdge,
            lostRegion);
        //element.free(lostRegion);
      }
    }
    return Collections.singleton(successor);
  }

  private boolean isEdgeToEntryPoint(CFAEdge edge)
  {
    // entry point not yet processed and encountering function start edge -> entry point reached
    return !entryPointProcessed && edge.getRawStatement().equals("Function start dummy edge");
  }

  private Collection<PointerElement> handleDeclaration(PointerElement element, CFAEdge edge, StorageClass storageClass, String name, IType specifier)
    throws CPATransferException
  {
    // ignore, this is a type definition, not a variable declaration
    if(storageClass == StorageClass.TYPEDEF)
      return soleSuccessor(element);

    // ignore struct prototypes
    if(isStructPrototype(name, specifier))
      return soleSuccessor(element);

    if(name == null)
      throw new UnrecognizedCCodeException("not expected in CIL", edge);

    if(specifier instanceof IASTFunctionTypeSpecifier)
      return soleSuccessor(element);

    if(isStructOnStack(specifier))
      return soleSuccessor(element);

    String varName = name;

    // handling arrays
    if(specifier instanceof IASTArrayTypeSpecifier)
    {
      Pointer pointerToArray = new Pointer(1);

      if(edge instanceof GlobalDeclarationEdge)
        element.addNewGlobalPointer(varName, pointerToArray);

      else
        element.addNewLocalPointer(varName, pointerToArray);

      //long length = parseIntegerLiteral(((IASTArrayDeclarator)declarator).)
      IType nestedSpecifier = ((IASTArrayTypeSpecifier)specifier).getType();
      if (!(nestedSpecifier instanceof IASTSimpleDeclSpecifier))
        throw new UnrecognizedCCodeException("unsupported array declaration", edge);

      IASTExpression lengthExpression = ((IASTArrayTypeSpecifier)specifier).getLength();
      if (!(lengthExpression instanceof IASTLiteralExpression))
        throw new UnrecognizedCCodeException("variable sized stack arrays are not supported", edge);

      long length = parseIntegerLiteral((IASTLiteralExpression)lengthExpression);

      StackArrayCell array = new StackArrayCell(element.getCurrentFunctionName(),
                                                  new StackArray(varName, length));

      element.pointerOp(new Pointer.Assign(array), pointerToArray);

      // store the pointer so the type analysis CPA can update its
      // type information
      missing = new MissingInformation();
      missing.typeInformationPointer = pointerToArray;
      missing.typeInformationEdge = edge;
      missing.typeInformationName = name;

    }

    // handle pointers
    else if(specifier instanceof IASTPointerTypeSpecifier)
    {
      int levelOfIndirection = 0;
      IType nestedSpecifier = specifier;
      do {
        nestedSpecifier = ((IASTPointerTypeSpecifier)nestedSpecifier).getType();
        levelOfIndirection++;
      } while (nestedSpecifier instanceof IASTPointerTypeSpecifier);

      // declaration of pointer to struct
      if(nestedSpecifier instanceof IASTElaboratedTypeSpecifier)
      {
        Pointer pointerToStruct = new Pointer(levelOfIndirection);

        if(edge instanceof GlobalDeclarationEdge)
        {
          element.addNewGlobalPointer(varName, pointerToStruct);
          element.pointerOp(new Pointer.Assign(Memory.UNINITIALIZED_POINTER), pointerToStruct);
        }

        // edge is instance of LocalDeclarationEdge
        else
        {
          element.addNewLocalPointer(varName, pointerToStruct);

          if(entryPointProcessed)
            element.pointerOp(new Pointer.Assign(Memory.UNINITIALIZED_POINTER), pointerToStruct);

          // pointer is a function parameter
          else
            element.pointerOp(new Pointer.Assign(Memory.UNKNOWN_POINTER), pointerToStruct);
        }

        missing = new MissingInformation();
        missing.typeInformationPointer = pointerToStruct;
        missing.typeInformationEdge = edge;
        missing.typeInformationName = name;
      }

      else
      {
        Pointer p = new Pointer(levelOfIndirection);
        if(edge instanceof GlobalDeclarationEdge)
        {
          element.addNewGlobalPointer(varName, p);
          element.pointerOp(new Pointer.Assign(Memory.UNINITIALIZED_POINTER), p);
        }

        else
        {
          element.addNewLocalPointer(varName, p);
          //if the entryFunction has not yet been processed, this means this pointer is a parameter
          //and should be considered unknown rather than uninitialized
          PointerTarget target = (!entryPointProcessed ? Memory.UNKNOWN_POINTER : Memory.UNINITIALIZED_POINTER);
          element.pointerOp(new Pointer.Assign(target), p);
        }

        // store the pointer so the type analysis CPA can update its
        // type information
        missing = new MissingInformation();
        missing.typeInformationPointer = p;
        missing.typeInformationEdge = edge;
        missing.typeInformationName = name;

        // initializers do not need to be considered, because they have to be
        // constant and constant pointers are considered null
        // local variables do not have initializers in CIL
      }
    }
    else
    {
      if(edge instanceof GlobalDeclarationEdge)
        element.addNewGlobalPointer(varName, null);

      else
        element.addNewLocalPointer(varName, null);
    }

    return soleSuccessor(element);
  }

  private void handleAssume(PointerElement element,
      IASTExpression expression, boolean isTrueBranch, AssumeEdge assumeEdge)
      throws UnrecognizedCCodeException, UnreachableStateException, InvalidPointerException {

    if (expression instanceof IASTBinaryExpression) {
      IASTBinaryExpression binaryExpression = (IASTBinaryExpression)expression;

      if (binaryExpression.getOperator() == BinaryOperator.EQUALS) {
        handleBinaryAssume(element, binaryExpression, isTrueBranch, assumeEdge);

      } else if (binaryExpression.getOperator() == BinaryOperator.NOT_EQUALS) {
        handleBinaryAssume(element, binaryExpression, !isTrueBranch, assumeEdge);

      } else {
        // assume it's not a pointer comparison
        return;
      }

    } else if (expression instanceof IASTUnaryExpression) {
      IASTUnaryExpression unaryExpression = (IASTUnaryExpression)expression;

      if (unaryExpression.getOperator() == UnaryOperator.NOT) {
        handleAssume(element, unaryExpression.getOperand(), !isTrueBranch,
            assumeEdge);

      } else if (unaryExpression.getOperator() == UnaryOperator.STAR) {
        // if (*var)
        String varName = expression.getRawSignature();
        Pointer p = element.lookupPointer(varName);

        if (p == null) {
          throw new UnrecognizedCCodeException("Trying to dereference a non-pointer variable",
              assumeEdge, expression);
        }

        boolean isNull = (p.contains(Memory.NULL_POINTER));
        boolean isUninitialized = p.contains(Memory.UNINITIALIZED_POINTER);

        if (isNull && p.getNumberOfTargets() == 1) {
          addError("Trying to dereference a NULL pointer" , assumeEdge);
        }

        if (isUninitialized && p.getNumberOfTargets() == 1) {
          // C actually allows this in special cases
          addWarning("Trying to dereference an uninitialized pointer" , assumeEdge, varName);
        }

        if (isTrueBranch) {
          // *p holds, i.e. *p != 0 holds, i.e. p cannot be NULL
          element.pointerOpAssumeInequality(p, Memory.NULL_POINTER);
        }

      } else {

        throw new UnrecognizedCCodeException("not expected in CIL", assumeEdge,
            expression);
      }
    } else if (expression instanceof IASTIdExpression) {
      // if (a)
      String varName = expression.getRawSignature();
      Pointer p = element.lookupPointer(varName);
      if (p == null) {
        // no pointer
        return;
      }
      boolean isNull = (p.contains(Memory.NULL_POINTER));

      if (isTrueBranch && isNull && p.getNumberOfTargets() == 1) {
        // p is always null here -> this branch is never reached
        throw new UnreachableStateException();
      }
      if (!isTrueBranch && !isNull) {
        // p is never null here -> this branch is never reached
        throw new UnreachableStateException();
      }

      if (isTrueBranch) {
        // p holds, i.e. p != 0 holds, i.e. p cannot point to null
        element.pointerOpAssumeInequality(p, Memory.NULL_POINTER);


      } else {
        // !p holds, i.e. p == 0 holds, i.e. p points to null
        element.pointerOpAssumeEquality(p, Memory.NULL_POINTER);
      }

    } else if (expression instanceof IASTCastExpression) {

      handleAssume(element, ((IASTCastExpression)expression).getOperand(), isTrueBranch,
          assumeEdge);

    }
  }

  private void handleBinaryAssume(PointerElement element,
      IASTBinaryExpression expression, boolean isTrueBranch,
      AssumeEdge assumeEdge) throws UnrecognizedCCodeException,
      UnreachableStateException {

    IASTExpression leftOp = expression.getOperand1();
    IASTExpression rightOp = expression.getOperand2();
    Pointer leftPointer = element.lookupPointer(leftOp.getRawSignature());
    Pointer rightPointer = element.lookupPointer(rightOp.getRawSignature());

    if (leftPointer != null && rightPointer != null) {

      if (element.areAliases(leftPointer, rightPointer)) {
        // surely equal

        if (!isTrueBranch) {
          // op1 != op2 is never true
          throw new UnreachableStateException();
        }

      } else if (leftPointer.isDifferentFrom(rightPointer)) {
        // never equal

        if (isTrueBranch) {
          // op1 == op2 is never true
          throw new UnreachableStateException();
        }

      } else {

        if (isTrueBranch) {
          element.pointerOpAssumeEquality(leftPointer, rightPointer);
        } else {
          element.pointerOpAssumeInequality(leftPointer, rightPointer);
        }

      }
    }
  }

  private void handleFunctionCall(PointerElement element,
      CFAEdge cfaEdge) throws UnrecognizedCCodeException {

    FunctionDefinitionNode funcDefNode = (FunctionDefinitionNode)cfaEdge.getSuccessor();
    String funcName = funcDefNode.getFunctionName();

    List<String> formalParameters = funcDefNode.getFunctionParameterNames();
    List<IASTExpression> actualParameters = ((FunctionCallEdge)cfaEdge).getArguments();

    // TODO: relocate parameter handling to strengthen operator

    if (formalParameters != null && formalParameters.size() > 0
        && !actualParameters.isEmpty()) {

      ArrayList<Pointer> actualValues = new ArrayList<Pointer>();

      assert formalParameters.size() == actualParameters.size();

      for (int i = 0; i < actualParameters.size(); i++) {
        IASTExpression parameter = actualParameters.get(i);

        if (parameter instanceof IASTIdExpression) {
          Pointer p = element.lookupPointer(parameter.getRawSignature());
          actualValues.add(p); // either a pointer or null

        } else if (parameter instanceof IASTLiteralExpression) {
          IASTLiteralExpression literal = (IASTLiteralExpression)parameter;

          if (literal instanceof IASTIntegerLiteralExpression
              && parseIntegerLiteral(literal) == 0) {

            actualValues.add(new Pointer()); // null pointer
          } else {
            actualValues.add(null); // probably not a pointer
          }

        } else if (parameter instanceof IASTUnaryExpression) {
          IASTUnaryExpression unaryExpression = (IASTUnaryExpression)parameter;

          if (unaryExpression.getOperator() == UnaryOperator.AMPER
              && unaryExpression.getOperand() instanceof IASTIdExpression) {

            String varName = unaryExpression.getOperand().getRawSignature();
            Variable var = element.lookupVariable(varName);
            actualValues.add(new Pointer(var));

          } else {
            throw new UnrecognizedCCodeException("not expected in CIL",
                cfaEdge, unaryExpression);
          }
        } else {
          throw new UnrecognizedCCodeException("not expected in CIL", cfaEdge,
              parameter);
        }
      }

      element.setScope(funcName);

      for (int i = 0; i < actualValues.size(); i++) {
        Pointer value = actualValues.get(i);
        if (value != null) {
          Pointer parameter = new Pointer();
          element.addNewLocalPointer(formalParameters.get(i), parameter); // sets location
          element.pointerOp(new Pointer.Assign(value), parameter);
        }
      }

    } else {
      element.setScope(funcName);
    }

    element.addNewLocalPointer(RETURN_VALUE_VARIABLE, null);
    element.addTemporaryTracking(RETURN_VALUE_VARIABLE, new Pointer());

    // always have MissingInformation because we do not know if the function
    // returns a pointer (and the sizeOfTargets of the parameters are not known
    // if there are any)
    missing = new MissingInformation();
  }

  private long parseIntegerLiteral(IASTLiteralExpression expression)
      throws UnrecognizedCCodeException {

    if (!(expression instanceof IASTIntegerLiteralExpression)) {
      throw new UnrecognizedCCodeException("integer expression expected", expression);
    }
    return ((IASTIntegerLiteralExpression)expression).asLong();
  }

  private void handleReturnFromFunction(PointerElement element,
      IASTFunctionCall expression, CFAEdge cfaEdge)
      throws UnrecognizedCCodeException {

    Pointer resultPointer = element.lookupPointer(RETURN_VALUE_VARIABLE);
    // resultPointer does not reliably indicate if the function returns a pointer!
    // E.g., without a type information CPA, return 0 will lead to a pointer result
    // even if the function returns an int

    // check for references to stack variables in result pointer
    if (resultPointer != null) {
      for (PointerTarget resultTarget : resultPointer.getTargets()) {
        if (resultTarget instanceof LocalVariable) {
          LocalVariable var = (LocalVariable)resultTarget;
          String function = element.getCurrentFunctionName();
          if (function.equals(var.getFunctionName())) {
            // function returns a reference to a local variable
            addWarning("Function " + function
                + " returns reference to local variable '" + var.getVarName()
                + "'", cfaEdge, resultTarget.toString());
          }
        }
      }
    }

    element.returnFromFunction(); // throw away local context

    // use function result
    if (expression instanceof IASTFunctionCallAssignmentStatement) {
      // a = func()
      IASTFunctionCallAssignmentStatement assignExpression = (IASTFunctionCallAssignmentStatement)expression;
      IASTExpression leftOperand = assignExpression.getLeftHandSide();

      if (leftOperand instanceof IASTIdExpression) {
        Pointer leftPointer =
            element.lookupPointer(leftOperand.getRawSignature());

        if (leftPointer != null) {
          if (resultPointer != null) {
            // do not use Assign(resultPointer) here, as this would try to make
            // resultPointer an alias of leftPointer
            element.pointerOp(new Pointer.AssignListOfTargets(resultPointer
                .getTargets()), leftPointer);
          } else {

            throw new UnrecognizedCCodeException(
                "assigning non-pointer value to pointer variable", cfaEdge,
                assignExpression);
          }
        } else {
          // function result is not assigned to a pointer, ignore
        }

      } else {
        // *x = func() etc.
        throw new UnrecognizedCCodeException("not expected in CIL", cfaEdge,
            assignExpression);
      }

    } else if (expression instanceof IASTFunctionCallStatement) {
      // func()
      // ignore
    } else {
      throw new UnrecognizedCCodeException(cfaEdge, expression.asStatement());
    }

    // check for memory leaks
    // TODO better location for calling checkMemoryLeak()? regularly? at end of analysis?
    Collection<MemoryRegion> lostRegions = element.checkMemoryLeak();

    for (MemoryRegion lostRegion : lostRegions) {
      addMemoryLeakWarning("Memory leak: " + lostRegion
          + " is not freed and has no known pointer towards it", cfaEdge,
          lostRegion);
      //element.free(lostRegion);
    }

  }

  private Collection<PointerElement> handleStatement(PointerElement successor, IASTStatement expression, StatementEdge cfaEdge)
      throws UnrecognizedCCodeException, InvalidPointerException
  {
    Collection<PointerElement> successors = null;

    if(expression instanceof IASTFunctionCallStatement)
    {
      // this is a mere function call (func(a))
      IASTFunctionCallExpression funcExpression = ((IASTFunctionCallStatement)expression).getFunctionCallExpression();
      String functionName = funcExpression.getFunctionNameExpression().getRawSignature();

      if(functionName.equals("free"))
        successors = handleFree(successor, funcExpression, cfaEdge);

      else if(functionName.equals("malloc"))
      {
        // malloc without assignment (will lead to memory leak)
        successor.addProperty(ElementProperty.MEMORY_LEAK);

        addWarning(Warning.MALLOC_WITHOUT_RETURN, cfaEdge, "");

        successors = soleSuccessor(successor);
      }
    }

    // statement is an assignment expression, e.g. a = b or a = a + b;
    else if(expression instanceof IASTAssignment)
      successors = handleAssignmentStatement(successor, (IASTAssignment)expression, cfaEdge);

    else
      throw new UnrecognizedCCodeException(cfaEdge, expression);

    return successors;
  }

  private Collection<PointerElement> handleFree(PointerElement successor, IASTFunctionCallExpression expression, CFAEdge cfaEdge)
      throws UnrecognizedCCodeException, InvalidPointerException
  {
    List<IASTExpression> parameters = expression.getParameterExpressions();

    if(parameters.size() != 1)
      throw new UnrecognizedCCodeException("Wrong number of arguments for free", cfaEdge, expression);

    IASTExpression parameter = parameters.get(0);

    if(parameter instanceof IASTIdExpression)
    {
      // the pointer to free
      Pointer pointer = successor.lookupPointer(parameter.getRawSignature());

      if(pointer == null)
        throw new UnrecognizedCCodeException("freeing non-pointer pointer", cfaEdge, parameter);

      // unused?
      //List<PointerTarget> newTargets = new ArrayList<PointerTarget>();

      boolean success = false;
      MemoryAddress freedMemory = null;

      for(PointerTarget target : pointer.getTargets())
      {
        if(target instanceof MemoryAddress)
        {
          freedMemory = (MemoryAddress)target;

          if(freedMemory.hasUnknownOffset())
          {
            addWarning("Possibly freeing pointer " + pointer.getLocation() + " to " + freedMemory + " with unknown offset", cfaEdge, freedMemory.toString());

            //newTargets.add(Memory.INVALID_POINTER);
            success = true; // it may succeed
            freedMemory = null; // but we cannot free it
          }

          else if(!freedMemory.hasZeroOffset())
            addWarning("Possibly freeing pointer " + pointer.getLocation() + " to " + freedMemory + " with offset != 0", cfaEdge, freedMemory.toString());

          else
            success = true;
        }

        // free(null) is allowed but does nothing
        else if(target.isNull())
          success = true;

        else if (target == Memory.UNKNOWN_POINTER)
          success = true;

        else
          addWarning("Possibly freeing pointer " + pointer.getLocation() + " to " + target, cfaEdge, target.toString());
      }

      // all targets fail
      if(!success)
      {
        // elevate the above warnings to an error
        successor.addProperty(ElementProperty.INVALID_FREE);
        throw new InvalidPointerException("Free of pointer " + pointer.getLocation() + " = " + pointer + " is impossible to succeed (all targets lead to errors)");
      }

      // free only if there is exactly one target and it is the beginning
      // of a memory region or the pointer has two targets and one of them
      // is the NULL-pointer (because malloc leaves us with at least one NULL-pointer. if the malloc result is unchecked)
      if((pointer.getNumberOfTargets() == 1 || (pointer.getNumberOfTargets() == 2 && pointer.contains(Memory.NULL_POINTER))) && (freedMemory != null))
      {
        try
        {
          successor.free(freedMemory.getRegion());
        }
        catch (InvalidPointerException e)
        {
          // catch the exception and add the DOUBLE_FREE flag, then re-throw
          successor.addProperty(ElementProperty.DOUBLE_FREE);
          throw e;
        }
      }

      // when the program continues after free(p), p can only contain INVALID, NULL or UNKNOWN targets,
      // depending on what it contained before (MemoryAddress, NULL or UNKNOWN respectively)
      //element.pointerOpForAllAliases(new Pointer.AssignListOfTargets(newTargets), p, false);
    }

    else
      throw new UnrecognizedCCodeException("not expected in CIL", cfaEdge, parameter);

    return soleSuccessor(successor);
  }

  private Collection<PointerElement> handleAssignmentStatement(PointerElement successor, IASTAssignment expression, CFAEdge cfaEdge)
      throws UnrecognizedCCodeException, InvalidPointerException {

    // left hand side
    IASTExpression leftExpression = expression.getLeftHandSide();
    String leftVarName = null;
    Pointer leftPointer;
    boolean leftDereference;

    if(leftExpression instanceof IASTIdExpression)
    {
      // a
      leftDereference = false;
      leftVarName     = leftExpression.getRawSignature();
      leftPointer     = successor.lookupPointer(leftVarName);
    }
    else if(leftExpression instanceof IASTUnaryExpression)
    {
      IASTUnaryExpression unaryExpression = (IASTUnaryExpression)leftExpression;

      // *a
      if(unaryExpression.getOperator() == UnaryOperator.STAR)
      {
        leftDereference = true;
        leftExpression  = unaryExpression.getOperand();

        // remove the cast
        boolean leftCast = false;
        if(leftExpression instanceof IASTCastExpression)
        {
          leftCast = true;
          leftExpression = ((IASTCastExpression)leftExpression).getOperand();
        }

        // ensure that left hand side is an identifier now
        if (!(leftExpression instanceof IASTIdExpression))
          throw new UnrecognizedCCodeException("not expected in CIL", cfaEdge, leftExpression);

        leftPointer = successor.lookupPointer(leftExpression.getRawSignature());
        leftVarName = leftExpression.getRawSignature();

        // left side is not a pointer
        if(leftPointer == null)
        {
          successor.addProperty(ElementProperty.UNSAFE_DEREFERENCE);

          if(!leftCast)
            throw new UnrecognizedCCodeException("dereferencing a non-pointer", cfaEdge, leftExpression);

          else
            addWarning("Casting non-pointer value " + leftExpression.getRawSignature() + " to pointer and dereferencing it", cfaEdge, leftExpression.getRawSignature());
        }

        // left side is a pointer
        else
        {
          if(!leftPointer.isDereferencable())
          {
            successor.addProperty(ElementProperty.UNSAFE_DEREFERENCE);

            throw new InvalidPointerException("Unsafe deref of pointer " + leftPointer.getLocation() + " = " + leftPointer);
          }

          if(!leftPointer.isSafe())
          {
            successor.addProperty(ElementProperty.POTENTIALLY_UNSAFE_DEREFERENCE);
            addWarning("Potentially unsafe dereferencing of pointer " + leftPointer.getLocation() + " = " + leftPointer, cfaEdge, unaryExpression.getRawSignature());

            // if program continues after dereferencing, pointer did not contain NULL, INVALID or UNINITIALIZED
            successor.pointerOpAssumeInequality(leftPointer, Memory.NULL_POINTER);
            successor.pointerOpAssumeInequality(leftPointer, Memory.INVALID_POINTER);
            successor.pointerOpAssumeInequality(leftPointer, Memory.UNINITIALIZED_POINTER);
          }

          // other pointers are not of interest
          if(!leftPointer.isPointerToPointer())
            leftPointer = null;
        }
      }

      else
        throw new UnrecognizedCCodeException("not expected in CIL", cfaEdge, unaryExpression);
    }

    // TODO fields, arrays
    else
      throw new UnrecognizedCCodeException("not expected in CIL", cfaEdge, leftExpression);

    // right hand side
    IASTRightHandSide op2 = expression.getRightHandSide();

    // handles *a = x and a = x
    handleAssignment(successor, leftVarName, leftPointer, leftDereference, op2, cfaEdge);

    // just added so that program compiles for now
    return soleSuccessor(successor);
  }

  /**
   * Handles an assignment, where the left-hand side is a pointer.
   * If the right-hand side seems to not evaluate to a pointer, the left pointer
   * is just set to unknown (no warning / error etc. is produced).
   */
  private void handleAssignment(PointerElement element,
      String leftVarName, Pointer leftPointer, boolean leftDereference,
      IASTRightHandSide expression, CFAEdge cfaEdge)
      throws UnrecognizedCCodeException, InvalidPointerException {

    if (expression instanceof IASTStringLiteralExpression) {
      // char* s = "hello world"
      // TODO we have currently no way of storing the information that this pointer
      // points to somewhere in the data region
      element.pointerOp(new Pointer.Assign(Memory.UNKNOWN_POINTER),
          leftPointer, leftDereference);

    } else if (expression instanceof IASTLiteralExpression) {
      // a = 0
      element.pointerOp(new Pointer.Assign(Memory.NULL_POINTER), leftPointer,
          leftDereference);

    } else if (expression instanceof IASTCastExpression) {
      // a = (int*)b
      // ignore cast, we do no type-checking
      handleAssignment(element, leftVarName, leftPointer, leftDereference,
                       ((IASTCastExpression)expression).getOperand(), cfaEdge);

    } else if (expression instanceof IASTFunctionCallExpression) {
      // a = func()

      IASTFunctionCallExpression funcExpression =
          (IASTFunctionCallExpression)expression;
      String functionName =
          funcExpression.getFunctionNameExpression().getRawSignature();

      if (functionName.equals("malloc")) {
        handleMalloc(element, leftPointer, leftDereference, funcExpression, cfaEdge);

      } else {
        // if it's an internal call, it's handled in handleReturnFromFunction()
        // it it's an external call and we do not know the function, we cannot
        // do more than set the pointer to unknown
        element.pointerOp(new Pointer.Assign(Memory.UNKNOWN_POINTER),
            leftPointer, leftDereference);
      }

    } else if (expression instanceof IASTBinaryExpression) {
      // a = b + c

      IASTBinaryExpression binExpression = (IASTBinaryExpression)expression;
      BinaryOperator typeOfOperator = binExpression.getOperator();
      IASTExpression op1 = binExpression.getOperand1();
      IASTExpression op2 = binExpression.getOperand2();

      if (op1 instanceof IASTCastExpression) {
        op1 = ((IASTCastExpression)op1).getOperand();
      }

      if (op1 instanceof IASTIdExpression) {
        Pointer rightPointer = element.lookupPointer(op1.getRawSignature());

        if (rightPointer == null) {
          if (leftPointer != null) {
            if (element.isPointerVariable(leftPointer.getLocation())) {
              addWarning("Assigning non-pointer value "
                  + binExpression.getRawSignature() + " to pointer "
                  + leftPointer.getLocation(), cfaEdge, binExpression
                  .getRawSignature());

              element.pointerOp(new Pointer.Assign(Memory.UNKNOWN_POINTER),
                  leftPointer, leftDereference);

            } else {
              // left hand side is a non-pointer variable which temporarily stored a pointer value
              element.removeTemporaryTracking(leftPointer.getLocation());
            }
          }

        } else {
          if (leftPointer == null) {
            // start tracking left hand side
            // assigning rightPointer is wrong, but at least it sets the correct
            // target size etc. and it will be overwritten anyway
            element.addTemporaryTracking(leftVarName, rightPointer);
            leftPointer = element.lookupPointer(leftVarName);
            assert leftPointer != null;
          }

          if (!(typeOfOperator == BinaryOperator.PLUS
              || typeOfOperator == BinaryOperator.MINUS)) {
            throw new UnrecognizedCCodeException(cfaEdge, binExpression);
          }

          if (op2 instanceof IASTLiteralExpression) {
            long offset = parseIntegerLiteral((IASTLiteralExpression)op2);
            if (typeOfOperator == BinaryOperator.MINUS) {
              offset = -offset;
            }

            element.pointerOp(new Pointer.AddOffsetAndAssign(rightPointer,
                offset), leftPointer);

          } else if (op2 instanceof IASTIdExpression) {
            missing = new MissingInformation();
            missing.actionLeftPointer = leftPointer;
            missing.actionRightPointer = rightPointer;
            missing.actionDereferenceFirst = leftDereference;
            missing.actionOffsetNegative =
                (typeOfOperator == BinaryOperator.MINUS);
            missing.actionASTNode = op2;

          } else {
            throw new UnrecognizedCCodeException("not expected in CIL",
                cfaEdge, op2);
          }
        }

      } else if (op1 instanceof IASTLiteralExpression) {

        if (leftPointer == null) {
          return;
        }

        if (op2 instanceof IASTLiteralExpression) {
          addWarning("Assigning non-pointer value "
              + binExpression.getRawSignature() + " to pointer "
              + leftPointer.getLocation(), cfaEdge, binExpression
              .getRawSignature());

          element.pointerOp(new Pointer.Assign(Memory.UNKNOWN_POINTER),
              leftPointer, leftDereference);

        } else {
          throw new UnrecognizedCCodeException("not expected in CIL", cfaEdge,
              op2);
        }

      } else {
        throw new UnrecognizedCCodeException("not expected in CIL", cfaEdge,
            op1);
      }

    } else if (expression instanceof IASTUnaryExpression) {
      IASTUnaryExpression unaryExpression = (IASTUnaryExpression)expression;
      UnaryOperator op = unaryExpression.getOperator();

      if (op == UnaryOperator.AMPER) {
        // a = &b
        Variable var =
            element.lookupVariable(unaryExpression.getOperand()
                .getRawSignature());

        element
            .pointerOp(new Pointer.Assign(var), leftPointer, leftDereference);

      } else if (op == UnaryOperator.MINUS) {
        if (leftPointer != null) {
          addWarning("Assigning non-pointer value "
              + unaryExpression.getRawSignature() + " to pointer "
              + leftPointer.getLocation(), cfaEdge, unaryExpression
              .getRawSignature());

          element.pointerOp(new Pointer.Assign(Memory.UNKNOWN_POINTER),
              leftPointer, leftDereference);

        }

      } else if (op == UnaryOperator.STAR) {
        // a = *b

        expression = unaryExpression.getOperand();

        boolean rightCast = false;
        if (expression instanceof IASTCastExpression) {
          rightCast = true;
          expression = ((IASTCastExpression)expression).getOperand();
        }

        if (!(expression instanceof IASTIdExpression)) {
          // not a variable at left hand side
          throw new UnrecognizedCCodeException("not expected in CIL", cfaEdge,
              expression);
        }

        Pointer rightPointer =
            element.lookupPointer(expression.getRawSignature());

        if (rightPointer == null) {

          if (!rightCast) {
            throw new UnrecognizedCCodeException("dereferencing a non-pointer",
                cfaEdge, expression);
          } else {
            addWarning("Casting non-pointer value "
                + expression.getRawSignature()
                + " to pointer and dereferencing it", cfaEdge, expression
                .getRawSignature());
          }

        } else {

          if (!rightPointer.isDereferencable()) {
            element.addProperty(ElementProperty.UNSAFE_DEREFERENCE);
            throw new InvalidPointerException("Unsafe deref of pointer "
                                              + rightPointer.getLocation()
                                              + " = " + rightPointer);
          }

          if (!rightPointer.isSafe()) {
            element.addProperty(ElementProperty.POTENTIALLY_UNSAFE_DEREFERENCE);
            addWarning("Potentially unsafe deref of pointer "
                + rightPointer.getLocation() + " = " + rightPointer, cfaEdge,
                unaryExpression.getRawSignature());

            // if program continues after deref, pointer did not contain NULL or INVALID or UNINITIALIZED
            element
                .pointerOpAssumeInequality(rightPointer, Memory.NULL_POINTER);
            element.pointerOpAssumeInequality(rightPointer,
                Memory.INVALID_POINTER);
            element.pointerOpAssumeInequality(rightPointer,
                Memory.UNINITIALIZED_POINTER);
          }

          if (leftPointer != null) {
            if (!rightPointer.isPointerToPointer()) {
              if (element.isPointerVariable(leftPointer.getLocation())) {
                addWarning("Assigning non-pointer value "
                    + unaryExpression.getRawSignature() + " to pointer "
                    + leftPointer.getLocation(), cfaEdge, expression
                    .getRawSignature());

                element.pointerOp(new Pointer.Assign(Memory.UNKNOWN_POINTER),
                    leftPointer, leftDereference);

              } else {
                // left hand side is a non-pointer variable which temporarily stored a pointer value
                element.removeTemporaryTracking(leftPointer.getLocation());
              }

            } else {
              element.pointerOp(new Pointer.DerefAndAssign(rightPointer),
                  leftPointer, leftDereference);
            }

          } else {
            // ignore assignment to non-pointer variable
          }
        }

      } else {
        throw new UnrecognizedCCodeException("not expected in CIL", cfaEdge,
            unaryExpression);
      }

    } else if (expression instanceof IASTIdExpression) {
      // a = b
      Pointer rightPointer =
          element.lookupPointer(expression.getRawSignature());

      if (leftPointer != null) {
        if (rightPointer == null) {
          if (element.isPointerVariable(leftPointer.getLocation())) {

            if (((IASTIdExpression)expression).getName().equals(
                "NULL")) {
              element.pointerOp(new Pointer.Assign(Memory.NULL_POINTER),
                  leftPointer, leftDereference);
            } else {
              element.pointerOp(new Pointer.Assign(Memory.UNKNOWN_POINTER),
                  leftPointer, leftDereference);
              addWarning("Assigning non-pointer value "
                  + expression.getRawSignature() + " to pointer "
                  + leftPointer.getLocation(), cfaEdge, expression
                  .getRawSignature());
            }

          } else {
            // left hand side is a non-pointer variable which temporarily stored a pointer value
            element.removeTemporaryTracking(leftPointer.getLocation());
          }

        } else {
          element.pointerOp(new Pointer.Assign(rightPointer), leftPointer,
              leftDereference);
        }
      } else {
        if (rightPointer != null && leftVarName != null) {
          element.addTemporaryTracking(leftVarName, rightPointer);
        }
      }

    } else {
      throw new UnrecognizedCCodeException("not expected in CIL", cfaEdge,
          expression);
    }

    // we can assume, that after any assignment the pointer is not uninitialized anymore ...
    // it either contains NULL, UNKNOWN or an actual pointer target
    if (leftPointer != null
        && leftPointer.contains(Memory.UNINITIALIZED_POINTER)) {
      element.pointerOpAssumeInequality(leftPointer,
          Memory.UNINITIALIZED_POINTER);

    }
  }

  /**
   * Does a malloc and allocates the result to the given pointer.
   *
   * @param element the abstract element
   * @param pointer the pointer for the result (may be null)
   * @param expression the parameter to the malloc call in the AST
   * @throws InvalidPointerException if malloc fails
   * @throws NumberFormatException if argument is a number, not a valid integer
   * @throws UnrecognizedCCodeException if parameter contains something unexpected
   */
  private void handleMalloc(PointerElement element, Pointer pointer,
      boolean leftDereference, IASTFunctionCallExpression expression, CFAEdge cfaEdge)
      throws UnrecognizedCCodeException {

    List<IASTExpression> parameters = expression.getParameterExpressions();
    if (parameters.size() != 1) {
      throw new UnrecognizedCCodeException("Wrong number of arguments for malloc", cfaEdge, expression);
    }
    IASTExpression parameter = parameters.get(0);

    Pointer.MallocAndAssign op = new Pointer.MallocAndAssign();
    element.pointerOp(op, pointer, leftDereference);
    MemoryAddress memAddress = op.getMallocResult();

    if (parameter instanceof IASTLiteralExpression) {
      long size = parseIntegerLiteral((IASTLiteralExpression)parameter);
      if (size < 0) {
        throw new UnrecognizedCCodeException("malloc with size < 0, but malloc takes unsigned parameter",
                                              cfaEdge, parameter);
      }
      if (size > 0x7FFFFFFF) {
        addWarning("Possible sign error: malloc with size > 2GB", cfaEdge,
            "malloc");
      }
      memAddress.getRegion().setLength(size);

    } else if (parameter instanceof IASTIdExpression) {
      // store variable name so the strengthen operator can update the length
      // information if he knows it

      missing = new MissingInformation();
      missing.mallocSizeMemory = memAddress;
      missing.mallocSizeASTNode = parameter;

    } else {
      throw new UnrecognizedCCodeException("not expected in CIL", cfaEdge,
          parameter);
    }
  }

  @Override
  public Collection<? extends AbstractElement> strengthen(
      AbstractElement element, List<AbstractElement> elements, CFAEdge cfaEdge,
      Precision precision) throws CPATransferException {

    if (missing == null) {
      return null;
    }

    if (!(element instanceof PointerElement)) {
      return null;
    }

    PointerElement pointerElement = (PointerElement)element;

    for (AbstractElement ae : elements) {
      try {
        if (ae instanceof ExplicitElement) {
          strengthen(pointerElement, (ExplicitElement)ae, cfaEdge,
              precision);

        } else if (ae instanceof TypesElement) {
          strengthen(pointerElement, (TypesElement)ae, cfaEdge, precision);
        }

      } catch (UnrecognizedCCodeException e) {
        addError(e.getMessage(), cfaEdge);
        return new ArrayList<AbstractElement>();

      } catch (InvalidPointerException e) {
        addError(e.getMessage(), cfaEdge);
        return new ArrayList<AbstractElement>();
      }
    }

    if (missing != null && missing.actionLeftPointer != null) {
      // strengthen operator did not get the necessary information
      // necessary do to the backup operation with unknown offset
      // this has to be here so it gets executed if there is no ExplicitCPA

      PointerOperation op;
      if (missing.actionRightPointer != null) {
        op = new Pointer.AddUnknownOffsetAndAssign(missing.actionRightPointer);
      } else {
        op = new Pointer.AddUnknownOffset();
      }
      pointerElement.pointerOp(op, missing.actionLeftPointer,
          missing.actionDereferenceFirst);
    }

    missing = null;
    return null;
  }

  /**
   * strengthen called for ExplicitCPA
   */
  private void strengthen(PointerElement pointerElement,
      ExplicitElement explicitElement, CFAEdge cfaEdge,
      Precision precision) throws InvalidPointerException,
      UnrecognizedCCodeException {

    if (missing.mallocSizeMemory != null) {
      Long value =
          getVariableContent(missing.mallocSizeASTNode, explicitElement,
              cfaEdge);
      if (value != null) {
        if (value < 0) {
          // TODO better exception or warning
          throw new UnrecognizedCCodeException("malloc with size < 0, "
              + "but malloc takes unsigned parameter", cfaEdge);
        }
        if (value > 0x7FFFFFFF) {
          addWarning("Possible sign error: malloc with size > 2GB", cfaEdge,
              "malloc");
        }
        missing.mallocSizeMemory.getRegion().setLength(value);
      }
    }

    if (missing.actionLeftPointer != null) {
      Long value =
          getVariableContent(missing.actionASTNode, explicitElement, cfaEdge);

      if (value != null) {
        long val = value.longValue();
        if (missing.actionOffsetNegative) {
          val = -val;
        }

        PointerOperation op;
        if (missing.actionRightPointer != null) {
          op = new Pointer.AddOffsetAndAssign(missing.actionRightPointer, val);

        } else {
          op = new Pointer.AddOffset(val);
        }
        pointerElement.pointerOp(op, missing.actionLeftPointer,
            missing.actionDereferenceFirst);

        // mark operation as completed successfully
        missing.actionLeftPointer = null;

      } else {
        // getting variable content failed
        // backup action (adding unknown offset) will be done by caller as if
        // there was no ExplicitCPA
      }
    }

  }

  private Long getVariableContent(IASTNode variable,
      ExplicitElement explicitElement, CFAEdge cfaEdge) {

    String varName = variable.getRawSignature();
    if (!explicitElement.contains(varName)) {
      varName = cfaEdge.getPredecessor().getFunctionName() + "::" + varName;
    }

    if (explicitElement.contains(varName)) {
      return explicitElement.getValueFor(varName);
    } else {
      return null;
    }
  }

  /**
   * strengthen called for TypesCPA
   */
  private void strengthen(PointerElement pointerElement,
      TypesElement typesElement, CFAEdge cfaEdge, Precision precision)
      throws UnrecognizedCCodeException {

    if (cfaEdge instanceof FunctionCallEdge) {
      // function call, adjust sizeOfTarget of parameters

      FunctionDefinitionNode funcDefNode =
          (FunctionDefinitionNode)cfaEdge.getSuccessor();
      String funcName = funcDefNode.getFunctionName();

      FunctionType function = typesElement.getFunction(funcName);
      for (String paramName : function.getParameters()) {
        Pointer pointer = pointerElement.lookupPointer(paramName);
        if (pointer != null) {
          Type type = function.getParameterType(paramName);

          setSizeOfTarget(pointer, type);
        }
      }
      if (function.getReturnType().getTypeClass() != Type.TypeClass.POINTER) {
        pointerElement.removeTemporaryTracking(pointerElement
            .lookupVariable(RETURN_VALUE_VARIABLE));
      }

    } else {

      if (missing.typeInformationPointer == null) {
        return;
      }

      // pointer variable declaration
      String functionName = cfaEdge.getSuccessor().getFunctionName();
      if (missing.typeInformationEdge instanceof GlobalDeclarationEdge) {
        functionName = null;
      }

      String varName = missing.typeInformationName;
      Type type = typesElement.getVariableType(functionName, varName);

      setSizeOfTarget(missing.typeInformationPointer, type);
    }
  }

  /**
   * TODO call, implementation
   * recursively traverses all fields of a struct
   */
  @SuppressWarnings("unused")
  private void handleStructDeclaration(PointerElement element,
                                       TypesElement typeElem, Type.CompositeType structType,
                                       String varName,String recursiveVarName) {

    Set<String> members = structType.getMembers();

    for (String member : members) {
      Type t = structType.getMemberType(member);
      //for a field that is itself a struct, repeat the whole process
      if (t != null && t.getTypeClass() == TypeClass.STRUCT) {
        handleStructDeclaration(element, typeElem, (Type.CompositeType)t, member,
            recursiveVarName + "." + member);
      } else {
        //TODO handle pointers
      }
    }
  }

  /**
   * checks all possible locations for type information of a given name
   */
  private Type findType(TypesElement typeElem, CFAEdge cfaEdge, String varName) {
    Type t = null;
    //check type definitions
    t = typeElem.getTypedef(varName);
    //if this fails, check functions
    if (t == null) {
      t = typeElem.getFunction(varName);
    }
    //if this also fails, check variables for the global context
    if (t == null) {
      t = typeElem.getVariableType(null, varName);
    }
    try {
      //if again there was no result, check local variables and function parameters
      if (t == null) {
        t = typeElem.getVariableType(cfaEdge.getSuccessor().getFunctionName(), varName);
      }
    } catch (IllegalArgumentException e) {
      //if nothing at all can be found, just return null
    }
    return t;
  }

  /**
   * TODO call
   * checks whether a given expression is a field reference;
   * if yes, find the type of the referenced field, if no, try to determine the type of the variable
   */
  @SuppressWarnings("unused")
  private Type checkForFieldReferenceType(IASTExpression exp, TypesElement typeElem,
                                          CFAEdge cfaEdge) {

    String name = exp.getRawSignature();
    Type t = null;

    if (exp instanceof IASTFieldReference) {
      String[] s = name.split("[.]");
      t = findType(typeElem, cfaEdge, s[0]);
      int i = 1;

      //follow the field reference to its end
      while (t != null && t.getTypeClass() == TypeClass.STRUCT && i < s.length) {
        t = ((Type.CompositeType)t).getMemberType(s[i]);
        i++;
      }

    //if exp is not a field reference, simply try to find the type of the associated variable name
    } else {
      t = findType(typeElem, cfaEdge, name);
    }
    return t;
  }

  /**
   * TODO call, implementation
   * recursively checks the fields of a struct being assigned to another struct of
   * the same type, setting the assignee's fields accordingly
   */
  @SuppressWarnings("unused")
  private void checkFields(PointerElement element, CFAEdge cfaEdge, IASTExpression exp,
                           TypesElement typeElem, Type.CompositeType structType,
                           String leftName, String rightName,
                           String recursiveLeftName, String recursiveRightName) {

    Set<String> members = structType.getMembers();

    //check all members
    for (String member : members) {
      Type t = structType.getMemberType(member);

      //for a field that is itself a struct, repeat the whole process
      if (t != null && t.getTypeClass() == TypeClass.STRUCT) {
        checkFields(element, cfaEdge, exp, typeElem, (Type.CompositeType)t, member, member,
                         recursiveLeftName + "." + member, recursiveRightName + "." + member);

      //else, check the assigned variable and set the assignee accordingly
      } else {
        //TODO handle copying of pointers
      }
    }
  }

  private void setSizeOfTarget(Pointer pointer, Type type) {

    switch (type.getTypeClass()) {

    case POINTER:
      Type targetType = ((PointerType)type).getTargetType();
      if (targetType.getTypeClass() == TypeClass.STRUCT) {
        pointer.setSizeOfTarget(1);
      } else {
        pointer.setSizeOfTarget(targetType.sizeOf());
      }
      break;

    case ARRAY:
      pointer.setSizeOfTarget(((ArrayType)type).getType().sizeOf());
      break;

    default:
      addWarning("Types determined by TypesCPA und PointerCPA differ!",
          null, pointer.getLocation().toString());
    }
  }

  public void setEntryFunctionDefinitionNode(FunctionDefinitionNode pEntryFunctionDefNode) {
    entryFunctionDefinitionNode = pEntryFunctionDefNode;
  }

  private static class Warning
  {
      private final static String MALLOC_WITHOUT_RETURN = "Memory leak because of calling malloc without using the return value!";
  };

  private Collection<PointerElement> soleSuccessor(PointerElement successor)
  {
    return Collections.singleton(successor);
  }

  private Collection<PointerElement> noSuccessors()
  {
    return Collections.emptySet();
  }

  private boolean isStructPrototype(String name, IType specifier)
  {
    return name == null && (specifier instanceof IASTElaboratedTypeSpecifier || specifier instanceof IASTCompositeTypeSpecifier);
  }

  private boolean isStructOnStack(IType specifier)
  {
    return (specifier instanceof IASTCompositeTypeSpecifier
            || specifier instanceof IASTElaboratedTypeSpecifier
            || specifier instanceof IASTEnumerationSpecifier);
  }
}