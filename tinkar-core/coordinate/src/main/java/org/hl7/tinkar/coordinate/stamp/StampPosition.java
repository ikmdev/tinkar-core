/* 
 * Licensed under the Apache License, Version 2.0 (the "License");
 *
 * You may not use this file except in compliance with the License.
 *
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributions from 2013-2017 where performed either by US government 
 * employees, or under US Veterans Health Administration contracts. 
 *
 * US Veterans Health Administration contributions by government employees
 * are work of the U.S. Government and are not subject to copyright
 * protection in the United States. Portions contributed by government 
 * employees are USGovWork (17USC §105). Not subject to copyright. 
 * 
 * Contribution by contractors to the US Veterans Health Administration
 * during this period are contractually contributed under the
 * Apache License, Version 2.0.
 *
 * See: https://www.usa.gov/government-works
 * 
 * Contributions prior to 2013:
 *
 * Copyright (C) International Health Terminology Standards Development Organisation.
 * Licensed under the Apache License, Version 2.0.
 *
 */



package org.hl7.tinkar.coordinate.stamp;

//~--- JDK imports ------------------------------------------------------------
import org.hl7.tinkar.common.service.PrimitiveData;
import org.hl7.tinkar.common.util.time.DateTimeUtil;
import org.hl7.tinkar.component.Concept;
import org.hl7.tinkar.entity.Entity;

import java.time.Instant;

//~--- interfaces -------------------------------------------------------------

/**
 * The class StampPosition.
 * An immutable class.
 *
 * @author kec
 */
public interface StampPosition
        extends Comparable<StampPosition> {

   /**
    * Gets the time.
    *
    * @return the time
    */
   long time();

   /**
    * Gets the time as instant.
    *
    * @return the time as instant
    */
   default Instant instant() {
      return DateTimeUtil.epochMsToInstant(time());
   }


   /**
    * Compare to.
    *
    * @param o the o
    * @return the int
    */
   @Override
   default int compareTo(StampPosition o) {
      final int comparison = Long.compare(this.time(), o.time());

      if (comparison != 0) {
         return comparison;
      }

      return Integer.compare(this.getPathForPositionNid(), o.getPathForPositionNid());
   }


   int getPathForPositionNid();

   /**
    * Gets the stamp path concept nid.
    *
    * @return the stamp path concept nid
    */
   default Concept getPathForPositionConcept() {
      return Entity.getFast(getPathForPositionNid());
   }


   StampPosition withTime(long time);
   StampPosition withPathForPositionNid(int pathForPositionNid);

   StampPositionRecord toStampPositionImmutable();

   default String toUserString() {
      final StringBuilder sb = new StringBuilder();
      sb.append(DateTimeUtil.format(time()));
      sb.append(" on ")
              .append(PrimitiveData.text(this.getPathForPositionNid()));
      return sb.toString();
   }
}

