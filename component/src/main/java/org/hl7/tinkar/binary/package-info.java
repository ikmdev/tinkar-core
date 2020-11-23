/*
 * Copyright 2020 kec.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * Package for reading and writing Tinkar objects to binary form. 
 * </br>
 * Marshaling: arrange or assemble (a group of people, especially soldiers) in order: the general marshaled his troops.
 * </br>
 * To "marshal" an object means to record its state and codebase(s)
 * in such a way that when the marshaled object is "unmarshaled," a
 * copy of the original object is obtained, possibly by automatically
 * loading the class definitions of the object.
 * </br>
 * The approach and annotations here are loosely based on solutions described in: 
 * https://cr.openjdk.java.net/~briangoetz/amber/serialization.html
 * although we don't have implementations described there to use at this time,
 * so we approximate them here.
 *
 * </br>
 *  Annotations in this package indicate which static method on a class shall be used as the
 *  Unmarshaler and which instance method shall be used as the Marshaler.
 *
 *  Both the Marshaler and Unmarshaler methods shall take a single parameter of type
 *  TinkarInput or TinkarOutput.
 * </br>
 * 
 * TinkarInput and TinkarOutput just add convenience methods on top of DataInputStream
 * and DataOutputStream (methods to efficiently write and array of UUIDs for example. 
 *
 */

package org.hl7.tinkar.binary;
