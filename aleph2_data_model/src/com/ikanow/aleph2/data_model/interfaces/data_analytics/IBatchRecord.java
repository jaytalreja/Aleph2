/*******************************************************************************
 * Copyright 2015, The IKANOW Open Source Project.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package com.ikanow.aleph2.data_model.interfaces.data_analytics;

import java.io.ByteArrayOutputStream;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;

/** The basic object type corresponding to batch processing (analytics and enrichment)
 * @author Alex
 */
public interface IBatchRecord {

	/** If available, returns the name of the analytic input
	 * @return the name of the analytic input, if available
	 */
	default Optional<String> getInputName() {
		return Optional.empty();
	}
	
	/** The JSON corresponding to the record
	 * @return The JSON corresponding to the record
	 */
	JsonNode getJson();
	
	/** For files, a stream containing the binary contents of the file
	 * @return For files, a stream containing the binary contents of the file
	 */
	default Optional<ByteArrayOutputStream> getContent() {
		return Optional.empty();		
	}
}
