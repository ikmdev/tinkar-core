package dev.ikm.tinkar.service.service;

import dev.ikm.tinkar.common.id.PublicId;

import java.util.List;

public interface TinkarPrimitive {

    /**
     * Returns a list of descendant concept IDs of the given parent concept ID.
     *
     * @param parentConceptId The parent concept ID.
     * @return A list of descendant concept IDs.
     */
    List<PublicId> descendantsOf(PublicId parentConceptId);

    /**
     * Returns a list of parent PublicIds of the given conceptId.
     *
     * @param conceptId the conceptId
     * @return a list of parent PublicIds
     */
    List<PublicId> parentsOf(PublicId conceptId);

    /**
     * Returns a list of PublicIds that the given member belongs to.
     *
     * @param member The PublicId of the member.
     * @return A list of PublicIds that the member belongs to.
     */
    List<PublicId> memberOf(PublicId member);

    /**
     * Returns a list of ancestor PublicIds of the given conceptId.
     *
     * @param conceptId the conceptId
     * @return a list of ancestor PublicIds
     */
    List<PublicId> ancestorOf(PublicId conceptId);

    /**
     * Returns a list of child PublicIds of the given parent PublicId.
     *
     * @param parentConceptId the parent PublicId
     * @return a list of child PublicIds
     */
    List<PublicId> childrenOf(PublicId parentConceptId);

    /**
     * Retrieves a list of Lidr record semantics from a test kit with the given
     * testKitConceptId.
     *
     * @param testKitConceptId the concept Id of the test kit
     * @return a list of PublicIds representing the Lidr record semantics
     */
    List<PublicId> getLidrRecordSemanticsFromTestKit(PublicId testKitConceptId);

    /**
     * Retrieves a list of PublicIds representing the result conformances from a
     * given LidrRecordConceptId.
     *
     * @param lidrRecordConceptId The PublicId of the LidrRecordConcept.
     * @return A list of PublicIds representing the result conformances.
     */
    List<PublicId> getResultConformancesFromLidrRecord(PublicId lidrRecordConceptId);

    /**
     * Retrieves a list of allowed results from a result conformance concept ID.
     *
     * @param resultConformanceConceptId The concept ID of the result conformance.
     * @return A list of public IDs representing the allowed results.
     */
    List<PublicId> getAllowedResultsFromResultConformance(PublicId resultConformanceConceptId);

    /**
     * Retrieves the descriptions of the given concept IDs.
     *
     * @param conceptIds the list of concept IDs
     * @return the list of descriptions for the given concept IDs
     */
    List<String> descriptionsOf(List<PublicId> conceptIds);

    /**
     * Retrieves the PublicId for a given concept.
     *
     * @param concept The concept for which to retrieve the PublicId.
     * @return The PublicId of the given concept.
     */
    PublicId getPublicId(String concept);

    /**
     * Retrieves the concept for a given PublicId.
     *
     * @param device The device for which to retrieve the PublicId.
     * @return The PublicId of the given device.
     */
    PublicId getPublicIdForDevice(String device);

    /**
     * Searches for PublicIds based on the provided search string and limit.
     *
     * @param search The search string used to find matching PublicIds.
     * @param limit  The maximum number of PublicIds to return.
     * @return A list of PublicIds that match the search criteria, limited by the
     *         specified number.
     */
    List<PublicId> search(String search, int limit) throws Exception;
    
}
