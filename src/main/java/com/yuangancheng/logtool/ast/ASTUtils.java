package com.yuangancheng.logtool.ast;

import com.sun.tools.javac.code.*;
import com.sun.tools.javac.jvm.ClassReader;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Name;
import com.sun.tools.javac.util.Names;
import com.sun.tools.javac.util.Pair;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class ASTUtils {

    private final Names names;
    private final Symtab symtab;
    private final ClassReader classReader;
    private final TreeMaker treeMaker;
    private static final Map<String, Type> baseTypeMap = new HashMap<>();
    private static final Map<String, TypeTag> baseTypeTagMap = new HashMap<>();
    public final JCTree.JCBlock EMPTY_BLOCK;

    public ASTUtils(Names names, Symtab symtab, ClassReader classReader, TreeMaker treeMaker) {
        this.names = names;
        this.symtab = symtab;
        this.classReader = classReader;
        this.treeMaker = treeMaker;
        if(baseTypeMap.size() == 0) {
            baseTypeMap.put("java.lang.String", symtab.stringType);
            baseTypeMap.put("byte", symtab.byteType);
            baseTypeMap.put("char", symtab.charType);
            baseTypeMap.put("short", symtab.shortType);
            baseTypeMap.put("long", symtab.longType);
            baseTypeMap.put("float", symtab.floatType);
            baseTypeMap.put("int", symtab.intType);
            baseTypeMap.put("double", symtab.doubleType);
            baseTypeMap.put("boolean", symtab.booleanType);

            baseTypeTagMap.put("byte", TypeTag.BYTE);
            baseTypeTagMap.put("char", TypeTag.CHAR);
            baseTypeTagMap.put("short", TypeTag.SHORT);
            baseTypeTagMap.put("long", TypeTag.LONG);
            baseTypeTagMap.put("float", TypeTag.FLOAT);
            baseTypeTagMap.put("int", TypeTag.INT);
            baseTypeTagMap.put("double", TypeTag.DOUBLE);
            baseTypeTagMap.put("boolean", TypeTag.BOOLEAN);
            baseTypeTagMap.put("void", TypeTag.VOID);
        }
        EMPTY_BLOCK = treeMaker.Block(0, List.nil());
    }

    private static class FlagsFieldType {
        public static Map<String, Long> map = new HashMap<>();

        static {
            map.put("Type$Method", (long)(Flags.ACYCLIC | Flags.PUBLIC));
        }
    }

    /**
     * Create an annotation
     *
     * @param qualifiedName the annotation's qualified name (e.g. "com...annotation.TestAnnotation")
     * @param keyValueMap the annotation's map of method name to its value
     * @param keyTypeQualifiedNameMap the annotation's map of method name to its qualified name
     * @return an instance of JCTree.JCAnnotation
     */
    public JCTree.JCAnnotation createAnnotation(String qualifiedName,
                                                Map<String, Object> keyValueMap,
                                                Map<String, String> keyTypeQualifiedNameMap) {
        Attribute propertiesAttribute = createCompound(qualifiedName, keyValueMap, keyTypeQualifiedNameMap);
        return treeMaker.Annotation(propertiesAttribute);
    }

    /**
     * Declare a variable
     *
     * @param flags the control flags of method
     * @param annotations the affiliated annotations of method
     * @param name the name of variable
     * @param varType the type of variable
     * @param initValExpression the initial value of variable
     * @return an instance of JCTree.JCVariableDecl
     */
    public JCTree.JCVariableDecl createVarDecl(long flags,
                                               List<JCTree.JCAnnotation> annotations,
                                               String name,
                                               String varType,
                                               JCTree.JCExpression initValExpression) {
        JCTree.JCModifiers modifiers = treeMaker.Modifiers(flags, annotations);
        Name varName = names.fromString(name);
        JCTree.JCExpression varTypeExpression;
        if(!varType.contains("[]")) {
            varTypeExpression = baseTypeTagMap.containsKey(varType) ?
                    treeMaker.TypeIdent(baseTypeTagMap.get(varType)) :
                    createCompleteFieldAccess(varType);
        }else{
            int cnt = 0;
            for(int i = 0; i < varType.length(); i++) {
                if(varType.charAt(i) == '[') {
                    cnt++;
                }
            }
            varTypeExpression = createArrayTypeTreeExpressionRecursively(varType.substring(0, varType.indexOf('[')), cnt);
        }
        return treeMaker.VarDef(modifiers, varName, varTypeExpression, initValExpression);
    }

    /**
     * Declare a method
     *
     * @param flags the control flags of method
     * @param annotations the affiliated annotations of method
     * @param resType the result type of method
     * @param defaultValue the default value of method when this method is declared in an annotation
     * @param name the name of method
     * @param typeParams
     * @param paramNameTypeMap the map of parameter's name to parameter's type
     * @param recvParam
     * @param exceptionThrownNameArrayList the list of thrown exceptions by method
     * @param methodBody the code block of method
     * @return an instance of JCTree.JCMethodDecl
     */
    public JCTree.JCMethodDecl createMethodDecl(long flags,
                                                List<JCTree.JCAnnotation> annotations,
                                                String resType,
                                                String defaultValue,
                                                String name,
                                                List<JCTree.JCTypeParameter> typeParams,
                                                LinkedHashMap<String, String> paramNameTypeMap,
                                                JCTree.JCVariableDecl recvParam,
                                                ArrayList<String> exceptionThrownNameArrayList,
                                                JCTree.JCBlock methodBody) {
        JCTree.JCModifiers modifiers = treeMaker.Modifiers(flags, annotations);
        Name methodName = names.fromString(name);
        JCTree.JCExpression resTypeExpression = baseTypeTagMap.containsKey(resType) ? treeMaker.TypeIdent(baseTypeTagMap.get(resType)) : treeMaker.Ident(names.fromString(resType));
        JCTree.JCExpression defaultValueExpression = null;
        if(defaultValue != null && !defaultValue.equals("")) {
            defaultValueExpression = treeMaker.Ident(names.fromString(defaultValue));
        }
        List<JCTree.JCVariableDecl> params = List.nil();
        for(Map.Entry<String, String> entry : paramNameTypeMap.entrySet()) {
            JCTree.JCVariableDecl jcVariableDecl = createVarDecl(Flags.PARAMETER, List.nil(), entry.getKey(), entry.getValue(), null);
            params = params.append(jcVariableDecl);
        }
        List<JCTree.JCExpression> exceptionThrown = List.nil();
        for(String string : exceptionThrownNameArrayList) {
            exceptionThrown = exceptionThrown.append(treeMaker.Ident(names.fromString(string)));
        }
        return treeMaker.MethodDef(modifiers,
                methodName,
                resTypeExpression,
                typeParams,
                recvParam,
                params,
                exceptionThrown,
                methodBody,
                defaultValueExpression
        );
    }

    public JCTree.JCWhileLoop createWhileLoopStatement(JCTree.JCExpression cond, JCTree.JCStatement body) {
        return treeMaker.WhileLoop(cond, body);
    }

    public JCTree.JCDoWhileLoop createDoWhileLoopStatement(JCTree.JCExpression cond, JCTree.JCStatement body) {
        return treeMaker.DoLoop(body, cond);
    }

    public JCTree.JCForLoop createForLoopStatement(List<JCTree.JCStatement> init,
                                                   JCTree.JCExpression condition,
                                                   List<JCTree.JCExpressionStatement> step,
                                                   JCTree.JCStatement body) {
        return treeMaker.ForLoop(init, condition, step, body);
    }

    public JCTree.JCCase createCaseStatement(JCTree.JCExpression pat, List<JCTree.JCStatement> stats) {
        return treeMaker.Case(pat, stats);
    }

    public JCTree.JCSwitch createSwitchStatement(JCTree.JCExpression selector, List<JCTree.JCCase> cases) {
        return treeMaker.Switch(selector, cases);
    }

    /**
     * Create a "if" statement
     *
     * @param condition the condition of "if" statement
     * @param thenPart the "then"'s statements part of "if" statement
     * @param elsePart the "else"'s statements part of "if" statement
     * @return an instance of JCTree.JCIF
     */
    public JCTree.JCIf createIfStatement(JCTree.JCExpression condition,
                                                JCTree.JCStatement thenPart,
                                                JCTree.JCStatement elsePart) {
        return treeMaker.If(condition, thenPart, elsePart);
    }

    public JCTree.JCCatch createCatch(JCTree.JCVariableDecl exception, JCTree.JCBlock body) {
        return treeMaker.Catch(exception, body);
    }

    public JCTree.JCTry createTryStatement(JCTree.JCBlock tryBody, List<JCTree.JCCatch> catchers, JCTree.JCBlock finalizerBody) {
        return treeMaker.Try(tryBody, catchers, finalizerBody);
    }

    public JCTree.JCSynchronized createSynchronizedStatement(JCTree.JCExpression lock, JCTree.JCBlock body) {
        return treeMaker.Synchronized(lock, body);
    }

    public JCTree.JCReturn createReturnStatement(JCTree.JCExpression returnedExpr) {
        return treeMaker.Return(returnedExpr);
    }

    public JCTree.JCExpressionStatement createBinaryStatement(JCTree.Tag opTag, JCTree.JCExpression lhs, JCTree.JCExpression rhs) {
        return treeMaker.Exec(createBinaryExpression(lhs, opTag, rhs));
    }

    public JCTree.JCExpressionStatement createUnaryStatement(JCTree.Tag opTag, JCTree.JCExpression jcExpression) {
        return treeMaker.Exec(createUnaryExpression(opTag, jcExpression));
    }

    public JCTree.JCExpression createUnaryExpression(JCTree.Tag opTag, JCTree.JCExpression jcExpression) {
        return treeMaker.Unary(opTag, jcExpression);
    }

    public JCTree.JCExpressionStatement createAssignStatement(JCTree.JCExpression lhs, JCTree.JCExpression rhs) {
        return treeMaker.Exec(treeMaker.Assign(lhs, rhs));
    }

    public JCTree.JCExpression createBinaryExpression(JCTree.JCExpression lhs, JCTree.Tag opTag, JCTree.JCExpression rhs) {
        return treeMaker.Binary(opTag, lhs, rhs);
    }

    public JCTree.JCExpression createTypeCastExpression(Type clazz, JCTree.JCExpression transformedExpression) {
        return treeMaker.TypeCast(clazz, transformedExpression);
    }

    public JCTree.JCExpression createParensExpression(JCTree.JCExpression wrappedExpression) {
        return treeMaker.Parens(wrappedExpression);
    }

    /**
     * Create an array dimension. e.g. []
     *
     * @param name array type name
     * @param dimensions the dimensions of array
     * @return an instance of JCTree.JCArrayTypeTree
     */
    public JCTree.JCExpression createArrayTypeTreeExpressionRecursively(String name, int dimensions) {
        JCTree.JCExpression arrTypeExpression = null;
        for(int i = 0; i < dimensions; i++) {
            if(i == 0) {
                arrTypeExpression = treeMaker.TypeArray(createIdent(name));
            }else{
                arrTypeExpression = treeMaker.TypeArray(arrTypeExpression);
            }
        }
        return arrTypeExpression;
    }

    /**
     * Create an array type
     *
     * @param name array type name
     * @param dimensions the dimensions of array
     * @param elementType the initial values' type of array (JCTree.JCIdent, JCTree.JCLiteral, or ArrayList)
     * @param elementValue the initial values of array
     * @return an instance of JCTree.JCNewArray
     */
    public JCTree.JCExpression createNewArrayExpression(String name, int dimensions, ArrayList<Object> elementType, ArrayList<Object> elementValue) {
        //Create array's dimensions. e.g. String[][][]
        JCTree.JCExpression arrTypeExpression = createArrayTypeTreeExpressionRecursively(name, dimensions - 1);

        /* the dimension of result array is one */
        if(arrTypeExpression == null) {
            arrTypeExpression = createIdent(name);
        }

        List<JCTree.JCExpression> elems = createInternalNewArrayExpressionRecursively(elementType, elementValue);
        return treeMaker.NewArray(arrTypeExpression, List.nil(), elems);
    }

    private List<JCTree.JCExpression> createInternalNewArrayExpressionRecursively(ArrayList<Object> elementType, ArrayList<Object> elementValue) {
        List<JCTree.JCExpression> elems = List.nil();
        for(int i = 0; i < elementType.size(); i++) {
            if(elementValue.get(i) instanceof ArrayList) {
                List<JCTree.JCExpression> nextElems = createInternalNewArrayExpressionRecursively((ArrayList<Object>)elementType.get(i), (ArrayList<Object>)elementValue.get(i));
                elems = elems.append(treeMaker.NewArray(null, List.nil(), nextElems));
            }else{
                elems = elems.append(elementType.get(i) == JCTree.JCIdent.class ? createIdent((String)elementValue.get(i)) : createLiteral(elementValue.get(i)));
            }
        }
        return elems;
    }

    public JCTree.JCVariableDecl createNewAssignStatement(String assignName,
                                                       String newClassName,
                                                       JCTree.JCExpression encl,
                                                       List<JCTree.JCExpression> typeArgs,
                                                       List<JCTree.JCExpression> args,
                                                       JCTree.JCClassDecl jcClassDecl) {
        return createVarDecl(0,
                List.nil(),
                assignName,
                newClassName,
                treeMaker.NewClass(encl, typeArgs, treeMaker.Ident(names.fromString(newClassName)), args, jcClassDecl)
        );
    }

    @SafeVarargs
    public final JCTree.JCBlock createBlock(List<JCTree.JCStatement> statementsList, List<JCTree.JCStatement>... statementsLists) {
        JCTree.JCBlock result = treeMaker.Block(0, statementsList);
        for (List<JCTree.JCStatement> list : statementsLists) {
            result.stats = result.stats.appendList(list);
        }
        return result;
    }

    public JCTree.JCStatement createMethodInvocationExpressionStatement(String completeFieldName, ArrayList<JCTree.JCExpression> paramsValueList) {
        return treeMaker.Exec(createMethodInvocation0(completeFieldName, paramsValueList));
    }

    /**
     * Create a method invocation
     *
     * @param completeFieldName complete field name (e.g. its representation like "System.out.println")
     * @param paramsValueList the arraylist of arguments
     * @return an instance of JCTree.JCMethodInvocation
     */
    public JCTree.JCExpression createMethodInvocation0(String completeFieldName, ArrayList<JCTree.JCExpression> paramsValueList) {
        if(completeFieldName == null || completeFieldName.equals("")) {
            return null;
        }
        List<JCTree.JCExpression> args = List.nil();
        for(JCTree.JCExpression jcExpression : paramsValueList) {
            args = args.append(jcExpression);
        }
        JCTree.JCExpression completeFieldAccess = createCompleteFieldAccess(completeFieldName);
        return treeMaker.Apply(List.nil(), completeFieldAccess, args);
    }

    public JCTree.JCExpression createMethodInvocation1(JCTree.JCExpression preExpression, String nextField, ArrayList<JCTree.JCExpression> paramsValueList) {
        List<JCTree.JCExpression> args = List.nil();
        for(JCTree.JCExpression jcExpression : paramsValueList) {
            args = args.append(jcExpression);
        }
        return treeMaker.Apply(List.nil(), createFieldAccess(preExpression, nextField), args);
    }

    public JCTree.JCExpression createIdent(String name) {
        return treeMaker.Ident(names.fromString(name));
    }

    public JCTree.JCExpression createLiteral(Object value) {
        return treeMaker.Literal(value);
    }

    /**
     * Create array access iteratively
     *
     * @param name the array access. e.g. a[0][1][2]
     * @return
     */
    public JCTree.JCExpression createArrayAccessIteratively(String name) {
        int leftParensPos = 0;
        while(name.charAt(leftParensPos) != '[') {
            leftParensPos++;
        }
        String arrayName = name.substring(0, leftParensPos);
        JCTree.JCExpression result = createIdent(arrayName);
        for(int rightParensPos = leftParensPos; rightParensPos < name.length(); rightParensPos++) {
            while(name.charAt(rightParensPos) != ']') {
                rightParensPos++;
            }
            String index = name.substring(leftParensPos + 1, rightParensPos);
            if(index.matches("^\\d+$")) {
                result = treeMaker.Indexed(result, createLiteral(Integer.parseInt(index)));
            }else{
                result = treeMaker.Indexed(result, createIdent(index));
            }
            leftParensPos = rightParensPos + 1;
        }
        return result;
    }

    /**
     * Create a complete field access
     *
     * @param completeFieldName complete name of field (e.g. "System.out.println" or "java.lang.System")
     * @return an instance of JCTree.JCFieldAccess
     */
    public JCTree.JCExpression createCompleteFieldAccess(String completeFieldName) {
        JCTree.JCExpression result;
        String[] splitNameArray = completeFieldName.split("\\.");
        if(splitNameArray[0].contains("[")) {
            result = createArrayAccessIteratively(splitNameArray[0]);
        }else{
            result = createIdent(splitNameArray[0]);
        }
        for(int i = 1; i < splitNameArray.length; i++) {
            if(splitNameArray[i].contains("[")) {
                int leftParensPos = 0;
                while(splitNameArray[i].charAt(leftParensPos) != '[') {
                    leftParensPos++;
                }
                result = treeMaker.Select(result, names.fromString(splitNameArray[i].substring(0, leftParensPos)));
                result = createInternalArrayAccess(result, splitNameArray[i].substring(leftParensPos));
            }else{
                result = treeMaker.Select(result, names.fromString(splitNameArray[i]));
            }
        }
        return result;
    }

    public JCTree.JCExpression createFieldAccess(JCTree.JCExpression preExpression, String nextField) {
        return treeMaker.Select(preExpression, names.fromString(nextField));
    }

    /**
     * Create array access for internal usage
     *
     * @param selected source select expression
     * @param brackets string. e.g. "[6][i][6]"
     * @return selected expression
     */
    private JCTree.JCExpression createInternalArrayAccess(JCTree.JCExpression selected, String brackets) {
        int leftParensPos = 0;
        for(int rightParensPos = leftParensPos + 1; rightParensPos < brackets.length(); rightParensPos++) {
            while(brackets.charAt(rightParensPos) != ']') {
                rightParensPos++;
            }
            String index = brackets.substring(leftParensPos, rightParensPos);
            if(index.matches("^\\d+$")) {
                selected = treeMaker.Indexed(selected, createLiteral(Integer.parseInt(index)));
            }else{
                selected = treeMaker.Indexed(selected, createIdent(index));
            }
            leftParensPos = rightParensPos + 1;
        }
        return selected;
    }

    /**
     * Create an instance of Attribute.Compound
     *
     * @param qualifiedName Canonical name of package or class, e.g. com....util.NameUtil
     * @param keyValueMap the map of property name to property value
     * @param keyQualifiedNameMap the map of property name to property type qualified name
     * @return an instance of Attribute.Compound
     */
    private Attribute createCompound(String qualifiedName, Map<String, Object> keyValueMap, Map<String, String> keyQualifiedNameMap) {
        List<Pair<Symbol.MethodSymbol, Attribute>> values = List.nil();
        Type classType = getClassType(qualifiedName);
        for(Map.Entry<String, Object> entry : keyValueMap.entrySet()) {
            Symbol.MethodSymbol first = createMethodSymbol(
                    keyQualifiedNameMap.get(entry.getKey()),
                    entry.getKey(),
                    List.nil(),
                    List.nil(),
                    Flags.ABSTRACT | Flags.PUBLIC,
                    classType.tsym
            );
            Attribute second = createConstant(getClassType(keyQualifiedNameMap.get(entry.getKey())),
                    keyValueMap.get(entry.getKey()));
            Pair<Symbol.MethodSymbol, Attribute> pair = new Pair<>(first, second);
            values = values.append(pair);
        }
        return new Attribute.Compound(classType, values);
    }

    public Attribute.Constant createConstant(Type type, Object value) {
        return new Attribute.Constant(type, value);
    }

    private Symbol.MethodSymbol createMethodSymbol(String resQualifiedName, String name, List<Type> argTypes, List<Type> thrown, long flags, Symbol ownerSymbol) {
        Type methodType = createMethodType(resQualifiedName, argTypes, thrown);
        return new Symbol.MethodSymbol(flags, names.fromString(name), methodType, ownerSymbol);

    }

    private Type.MethodType createMethodType(String resQualifiedName, List<Type> argTypes, List<Type> thrown) {
        Type resType = getClassType(resQualifiedName);
        Type temp = createClassType0(FlagsFieldType.map.get("Type$Method"), "Method", symtab.noSymbol);
        return new Type.MethodType(argTypes, resType, thrown, temp.tsym);
    }

    public Type getClassType(String qualifiedName) {
        if(baseTypeMap.containsKey(qualifiedName)) {
            return baseTypeMap.get(qualifiedName);
        }else{
            Symbol.ClassSymbol classSymbol = classReader.enterClass(names.fromString(qualifiedName));
            return createClassType1(classSymbol);
        }
    }

    private Symbol.TypeSymbol createClassSymbol(long flags, String className, Type classType, Symbol ownerSymbol) {
        return new Symbol.ClassSymbol(flags, names.fromString(className), classType, ownerSymbol);
    }

    private Type createClassType0(long flags, String className, Symbol ownerSymbol) {
        Type.ClassType classType = new Type.ClassType(Type.noType, List.nil(), null);
        classType.tsym = createClassSymbol(flags, className, classType, ownerSymbol);
        return  classType;
    }

    public Type createClassType1(Symbol.TypeSymbol typeSymbol) {
        return new Type.ClassType(Type.noType, List.nil(), typeSymbol);
    }
}
