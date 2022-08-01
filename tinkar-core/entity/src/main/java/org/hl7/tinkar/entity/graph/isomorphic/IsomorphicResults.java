package org.hl7.tinkar.entity.graph.isomorphic;
//~--- JDK imports ------------------------------------------------------------

import org.hl7.tinkar.entity.graph.DiTreeEntity;
import org.hl7.tinkar.entity.graph.EntityVertex;

import java.util.List;

//~--- interfaces -------------------------------------------------------------

/**
 * Computed results of an isomorphic comparison of two expressions: the
 * reference expression and the comparison expression.
 * @author kec
 */
public interface IsomorphicResults {
    /**
     * Gets the added relationship roots.
     *
     * @return roots for connected nodes that comprise is-a, typed relationships, or relationship groups that are
     *  in the referenceExpression, but not in the comparisonExpression.
     */
    List<EntityVertex> getAddedRelationshipRoots();

    /**
     * Gets the additional node roots.
     *
     * @return roots for connected nodes that are in the reference expression, but not in the
     * common expression.
     */
    List<EntityVertex> getAdditionalVertexRoots();

    /**
     * Gets the comparison expression.
     *
     * @return the expression that is compared to the reference expression to compute
     * isomorphic results.
     */
    DiTreeEntity getComparisonTree();

    /**
     * Gets the deleted node roots.
     *
     * @return roots for connected nodes that are in the comparison expression, but are not in
     * the common expression.
     */
    List<EntityVertex> getDeletedVertexRoots();

    /**
     * Gets the deleted relationship roots.
     *
     * @return roots for connected nodes that comprise is-a, typed relationships, or relationship groups that are
     * in the comparisonExpression, but not in the referenceExpression.
     */
    List<EntityVertex> getDeletedRelationshipRoots();

    /**
     * Gets the isomorphic expression.
     *
     * @return an expression containing only the connected set of nodes representing
     *  the maximal common isomorphism between the two expressions that are connected
     *  to their respective roots.
     */
    DiTreeEntity getIsomorphicTree();

    /**
     *
     *   @return an expression containing a merger of all the nodes in the reference and comparison expression.
     */
    DiTreeEntity getMergedTree();

    /**
     * Gets the reference expression.
     *
     * @return the expression that isomorphic results are computed with respect to.
     */
    DiTreeEntity getReferenceTree();

    /**
     * Gets the shared relationship roots.
     *
     * @return roots for connected nodes that comprise is-a, typed relationships, or relationship groups that are
     *  in both the referenceExpression and in the comparisonExpression.
     */
    List<EntityVertex> getSharedRelationshipRoots();

    /**
     *
     * @return true if the evaluation expressions are equivalent.
     */
    boolean equivalent();
}

