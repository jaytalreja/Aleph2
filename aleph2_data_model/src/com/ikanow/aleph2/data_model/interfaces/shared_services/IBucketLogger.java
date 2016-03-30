/*******************************************************************************
 * Copyright 2016, The IKANOW Open Source Project.
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
package com.ikanow.aleph2.data_model.interfaces.shared_services;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

import org.apache.logging.log4j.Level;

import scala.Tuple2;

import com.ikanow.aleph2.data_model.objects.shared.BasicMessageBean;

/**
 * @author Burch
 *
 */
public interface IBucketLogger {
	//simple interfaces
	public CompletableFuture<?> log(final Level level, final boolean success, final Supplier<String> message, final Supplier<String> subsystem);
	public CompletableFuture<?> log(final Level level, final boolean success, final Supplier<String> message, final Supplier<String> subsystem, final Supplier<String> command);
	public CompletableFuture<?> log(final Level level, final boolean success, final Supplier<String> message, final Supplier<String> subsystem, final Supplier<String> command, final Supplier<Integer> messageCode);
	public CompletableFuture<?> log(final Level level, final boolean success, final Supplier<String> message, final Supplier<String> subsystem, final Supplier<String> command, final Supplier<Integer> messageCode, final Supplier<Map<String,Object>> details);
	
	//other interfaces?
	public CompletableFuture<?> inefficientLog(final Level level, final BasicMessageBean message);
	public CompletableFuture<?> log(final Level level, final IBasicMessageBeanSupplier message);
	
	
	//complex interface
	public CompletableFuture<?> log(final Level level, final IBasicMessageBeanSupplier message, final String merge_key, final BiFunction<BasicMessageBean, BasicMessageBean, BasicMessageBean> merge_operation, final Optional<Function<Tuple2<BasicMessageBean, Map<String,Object>>, Boolean>> rule_function);
	
	//util interface
	public CompletableFuture<?> flush();
}
