package com.sourcegraph.langp.javac;

import com.sun.source.util.Trees;
import com.sun.tools.javac.tree.JCTree;

import java.util.Collection;

class CursorScanner extends BaseScanner {

    protected final long cursor;
    protected final Trees trees;

    public CursorScanner(long cursor, Trees trees) {
        super();
        this.cursor = cursor;
        this.trees = trees;
    }

    @Override
    public void scan(JCTree tree) {
        if (containsCursor(tree))
            super.scan(tree);
    }

    protected boolean containsCursor(JCTree node) {
        if (trees == null) {
            return false;
        }
        long start = trees.getSourcePositions().getStartPosition(compilationUnit, node);
        long end = trees.getSourcePositions().getEndPosition(compilationUnit, node);

        return start <= cursor && cursor <= end;
    }

    protected boolean containsCursor(Collection<? extends JCTree> node) {
        for (JCTree t : node) {
            if (containsCursor(t))
                return true;
        }

        return false;
    }
}
