package org.hl7.tinkar.component.graph;

/**
 * Adaptor classes must rely on vertex properties for any persistent fields, otherwise persistence and
 * transactions will break.
 *
 * @param <A>
 */
public interface GraphAdaptorFactory<A> {
    A adapt(Graph graph);
}
