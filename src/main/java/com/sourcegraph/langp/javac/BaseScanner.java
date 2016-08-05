package com.sourcegraph.langp.javac;

import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeScanner;
import com.sun.tools.javac.util.List;

/**
 * Base scanner
 */
class BaseScanner extends TreeScanner {

    protected JCTree.JCCompilationUnit compilationUnit;

    public BaseScanner() {
    }

    @Override
    public void visitTopLevel(JCTree.JCCompilationUnit tree) {
        this.compilationUnit = tree;

        super.visitTopLevel(tree);
    }

    @Override
    public void scan(JCTree node) {
        if (node != null) {
            node.accept(this);
        }
    }

    @Override
    public void scan(List<? extends JCTree> nodes) {
        if (nodes != null) {
            for (JCTree node : nodes)
                scan(node);
        }
    }

    @Override
    public void visitErroneous(JCTree.JCErroneous tree) {
        scan(tree.errs);
    }
}
