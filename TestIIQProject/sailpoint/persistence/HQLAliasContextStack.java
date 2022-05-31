/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.persistence;

import java.util.Stack;

/**
 * An HQLAliasContext that stores a stack of HQLAliasContexts and delegates to
 * the context on the top of the stack.  In addition to the HQLAliasContext
 * methods, this has a few methods to manage the stack.
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
class HQLAliasContextStack implements HQLAliasContext {

    private Stack<HQLAliasContext> stack;

    
    /**
     * Constructor.
     * 
     * @param  initialCtx  The HQLAliasContext to stick on the stack.
     */
    public HQLAliasContextStack(HQLAliasContext initialCtx) {
        this.stack = new Stack<HQLAliasContext>();
        this.stack.add(initialCtx);
    }

    /**
     * Push the given context onto the stack.
     * 
     * @param  ctx  The HQLAliasContext to push onto the stack.
     */
    public void push(HQLAliasContext ctx) {
        this.stack.push(ctx);
    }

    /**
     * Pop the current HQLAliasContext off the top of the stack.
     */
    public void pop() {
        this.stack.pop();
    }

    /**
     * Retrieve the HQLAliasContext that is currently on the top of the stack.
     * 
     * @return The HQLAliasContext that is currently on the top of the stack.
     */
    public HQLAliasContext getCurrent() {
        return this.stack.peek();
    }

    /* (non-Javadoc)
     * @see sailpoint.persistence.HQLAliasContext#getAlias(java.lang.Class)
     */
    public String getAlias(Class clazz) {
        return this.getCurrent().getAlias(clazz);
    }

    /* (non-Javadoc)
     * @see sailpoint.persistence.HQLAliasContext#getAlias(java.lang.String)
     */
    public String getAlias(String propertyPath) {
        return this.getCurrent().getAlias(propertyPath);
    }

    /* (non-Javadoc)
     * @see sailpoint.persistence.HQLAliasContext#setClassAlias(java.lang.Class, java.lang.String)
     */
    public void setClassAlias(Class clazz, String alias) {
        this.getCurrent().setClassAlias(clazz, alias);
    }

    /* (non-Javadoc)
     * @see sailpoint.persistence.HQLAliasContext#getDefaultAlias()
     */
    public String getDefaultAlias() {
        return this.getCurrent().getDefaultAlias();
    }

    /* (non-Javadoc)
     * @see sailpoint.persistence.HQLAliasContext#setInsideOr(boolean)
     */
    public boolean setInsideOr(boolean insideOr) {
        return this.getCurrent().setInsideOr(insideOr);
    }

    /* (non-Javadoc)
     * @see sailpoint.persistence.HQLAliasContext#substituteAlias(java.lang.String)
     */
    public String substituteAlias(String propertyPath) {
        return this.getCurrent().substituteAlias(propertyPath);
    }

    /* (non-Javadoc)
     * @see sailpoint.persistence.HQLAliasContext#substituteAlias(java.lang.String, boolean, boolean)
     */
    public String substituteAlias(String propertyPath, boolean forceUniqueJoin,
                                  boolean forceOuterJoin) {
        return this.getCurrent().substituteAlias(propertyPath, forceUniqueJoin, forceOuterJoin);
    }
}
