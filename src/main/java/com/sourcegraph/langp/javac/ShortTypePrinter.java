package com.sourcegraph.langp.javac;

import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import org.apache.commons.lang3.StringUtils;

import javax.lang.model.element.ElementKind;
import javax.lang.model.type.*;
import javax.lang.model.util.AbstractTypeVisitor8;
import java.util.Collection;
import java.util.LinkedList;
import java.util.stream.Collectors;

public class ShortTypePrinter extends AbstractTypeVisitor8<String, Void> {

    private ShortTypePrinter() {

    }

    public static String print(TypeMirror type) {
        return type.accept(new ShortTypePrinter(), null);
    }

    public static String methodSignature(Symbol.MethodSymbol e) {
        String name = e.getSimpleName().toString();
        boolean varargs = (e.flags() & Flags.VARARGS) != 0;
        Collection<String> params = new LinkedList<>();

        com.sun.tools.javac.util.List<Symbol.VarSymbol> parameters = e.getParameters();
        for (int i = 0; i < parameters.size(); i++) {
            Symbol.VarSymbol p = parameters.get(i);
            String pName = shortName(p, varargs && i == parameters.size() - 1);

            params.add(pName);
        }

        String signature = name + "(" + StringUtils.join(params, ", ") + ")";

        if (!e.getThrownTypes().isEmpty()) {
            Collection<String> thrown = new LinkedList<>();

            for (Type t : e.getThrownTypes())
                thrown.add(ShortTypePrinter.print(t));

            signature += " throws " + StringUtils.join(thrown, ", ") ;
        }

        return signature;
    }

    @Override
    public String visitIntersection(IntersectionType t, Void aVoid) {
        return t.getBounds().stream().map(ShortTypePrinter::print).collect(Collectors.joining(" & "));
    }

    @Override
    public String visitUnion(UnionType t, Void aVoid) {
        return t.getAlternatives().stream().map(ShortTypePrinter::print).collect(Collectors.joining(" | "));
    }

    @Override
    public String visitPrimitive(PrimitiveType t, Void aVoid) {
        return t.toString();
    }

    @Override
    public String visitNull(NullType t, Void aVoid) {
        return t.toString();
    }

    @Override
    public String visitArray(ArrayType t, Void aVoid) {
        return print(t.getComponentType()) + "[]";
    }

    @Override
    public String visitDeclared(DeclaredType t, Void aVoid) {
        String result = "";

        // If type is an inner class, add outer class name
        if (t.asElement().getKind() == ElementKind.CLASS &&
            t.getEnclosingType().getKind() == TypeKind.DECLARED) {

            result += print(t.getEnclosingType()) + ".";
        }

        result += t.asElement().getSimpleName().toString();

        if (!t.getTypeArguments().isEmpty()) {
            String params = t.getTypeArguments()
                              .stream()
                              .map(ShortTypePrinter::print)
                              .collect(Collectors.joining(", "));

            result += "<" + params + ">";
        }

        return result;
    }

    @Override
    public String visitError(ErrorType t, Void aVoid) {
        return "???";
    }

    @Override
    public String visitTypeVariable(TypeVariable t, Void aVoid) {
        return t.asElement().toString();
    }

    @Override
    public String visitWildcard(WildcardType t, Void aVoid) {
        String result = "?";

        if (t.getSuperBound() != null)
            result += " super " + print(t.getSuperBound());

        if (t.getExtendsBound() != null)
            result += " extends " + print(t.getExtendsBound());

        return result;
    }

    @Override
    public String visitExecutable(ExecutableType t, Void aVoid) {
        return t.toString();
    }

    @Override
    public String visitNoType(NoType t, Void aVoid) {
        return t.toString();
    }

    private static String shortName(Symbol.VarSymbol p, boolean varargs) {
        Type type = p.type;

        if (varargs) {
            Type.ArrayType array = (Type.ArrayType) type;

            type = array.getComponentType();
        }

        String acc = shortTypeName(type);
        String name = p.name.toString();

        if (varargs)
            acc += "...";

        if (!name.matches("arg\\d+"))
            acc += " " + name;

        return acc;
    }

    private static String shortTypeName(Type type) {
        return print(type);
    }

}
