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
 ******************************************************************************/
package com.ikanow.aleph2.data_import_manager.analytics.utils;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.locks.InterProcessMutex;

import scala.Tuple2;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.ikanow.aleph2.data_model.interfaces.shared_services.ICrudService;
import com.ikanow.aleph2.data_model.objects.data_analytics.AnalyticThreadTriggerBean.AnalyticThreadComplexTriggerBean.TriggerType;
import com.ikanow.aleph2.data_model.objects.data_import.DataBucketBean;
import com.ikanow.aleph2.data_model.utils.CrudUtils;
import com.ikanow.aleph2.data_model.utils.CrudUtils.QueryComponent;
import com.ikanow.aleph2.data_model.utils.Lambdas;
import com.ikanow.aleph2.data_model.utils.Tuples;
import com.ikanow.aleph2.management_db.data_model.AnalyticTriggerStateBean;
import com.ikanow.aleph2.management_db.utils.ActorUtils;

/** A set of utilities for retrieving and updating the trigger state
 * @author Alex
 */
public class AnalyticTriggerCoreUtils {
	protected final static Cache<String, InterProcessMutex> _mutex_cache = CacheBuilder.newBuilder().expireAfterAccess(2, TimeUnit.HOURS).build();

	/** Get a list of triggers indexed by bucket (plus host for host-locked jobs) to examine for completion/triggering
	 *  May at some point need to update the next_check time on the underlying database records (currently doesn't, we'll let the interprocess mutex sort that out)
	 *  Here's the set of triggers that we want to get
	 *  1) External triggers for inactive buckets
	 *  2) Internal triggers for active buckets 
	 *  3) Active jobs
	 * @param trigger_crud
	 * @return
	 */
	public static CompletableFuture<Map<String, List<AnalyticTriggerStateBean>>> getTriggersToCheck(final ICrudService<AnalyticTriggerStateBean> trigger_crud) {		
		//TODO (ALEPH-12) - add these to the optimized list in singleton
		
		//TODO (ALEPH-12): errr pretty sure all these need to include a "date" term....
		
		final QueryComponent<AnalyticTriggerStateBean> active_job_query =
				CrudUtils.allOf(AnalyticTriggerStateBean.class)
					.when(AnalyticTriggerStateBean::is_job_active, true)
					.when(AnalyticTriggerStateBean::trigger_type, TriggerType.none);
		
		final QueryComponent<AnalyticTriggerStateBean> external_query =
				CrudUtils.allOf(AnalyticTriggerStateBean.class)
					.when(AnalyticTriggerStateBean::is_bucket_active, false)
					.when(AnalyticTriggerStateBean::is_bucket_suspended, false)
					.withNotPresent(AnalyticTriggerStateBean::job_name);
				
		final QueryComponent<AnalyticTriggerStateBean> internal_query =
				CrudUtils.allOf(AnalyticTriggerStateBean.class)
					.when(AnalyticTriggerStateBean::is_bucket_active, true)
					.withPresent(AnalyticTriggerStateBean::job_name);
						
		final QueryComponent<AnalyticTriggerStateBean> query = CrudUtils.anyOf(Arrays.asList(active_job_query, external_query, internal_query));
		
		return trigger_crud.getObjectsBySpec(query).thenApply(cursor -> 
				StreamSupport.stream(cursor.spliterator(), false)
							.collect(Collectors.<AnalyticTriggerStateBean, String>
								groupingBy(trigger -> trigger.bucket_name() + ":" + trigger.locked_to_host())));		
	}
	
	/** Just deletes everything for a deleted bucket
	 *  May need to make this cleverer at some point
	 * @param trigger_crud
	 * @param bucket
	 */
	public static void deleteTriggers(final ICrudService<AnalyticTriggerStateBean> trigger_crud, final DataBucketBean bucket) {
		trigger_crud.deleteObjectsBySpec(CrudUtils.allOf(AnalyticTriggerStateBean.class).when(AnalyticTriggerStateBean::bucket_name, bucket.full_name()));
	}
	
	/** Removes any triggers other users might have ownership over and grabs ownership
	 * @param all_triggers - triggers indexed by bucket (+host for host-locked jobs)
	 * @param process_id
	 * @param curator
	 * @param mutex_fail_handler - sets the duration of the mutex wait, and what to do if it fails (ie for triggers, short wait and do nothing; for bucket message wait for longer and save them to a retry queue)
	 * @return - filtered trigger set, still indexed by bucket
	 */
	public static Map<String, List<AnalyticTriggerStateBean>> registerOwnershipOfTriggers(
			final Map<String, List<AnalyticTriggerStateBean>> all_triggers, 
			final String process_id,
			final CuratorFramework curator, 
			final Tuple2<Duration, Consumer<String>> mutex_fail_handler
			)
	{		
		return all_triggers.entrySet()
			.stream() //(can't be parallel - has to happen in the same thread)
			.map(kv -> Tuples._2T(kv, ActorUtils.BUCKET_ANALYTICS_TRIGGER_ZOOKEEEPER + kv.getKey()))
			.flatMap(Lambdas.flatWrap_i(kv_path -> 
						Tuples._2T(kv_path._1(), _mutex_cache.get(kv_path._2(), () -> { return new InterProcessMutex(curator, kv_path._2()); }))))
			.flatMap(Lambdas.flatWrap_i(kv_mutex -> {
				if (kv_mutex._2().acquire(mutex_fail_handler._1().getSeconds(), TimeUnit.SECONDS)) {
					return kv_mutex._1();
				}
				else {
					mutex_fail_handler._2().accept(kv_mutex._1().getKey()); // (run this synchronously, the callable can always run in a different thread if it wants to)
					throw new RuntimeException(""); // (leaves the flatWrap empty)
				}
			}))
			.collect(Collectors.toMap(kv -> kv.getKey(), kv -> kv.getValue()))
			;
	}
	
	/** Deregister interest in triggers once we have completed processing them
	 * @param job_names 
	 * @param curator
	 */
	public static void deregisterOwnershipOfTriggers(final Collection<String> path_names, CuratorFramework curator) {
		path_names.stream() //(can't be parallel - has to happen in the same thread)
			.map(path -> ActorUtils.BUCKET_ANALYTICS_TRIGGER_ZOOKEEEPER + path)
			.map(path -> Tuples._2T(path, _mutex_cache.getIfPresent(path)))
			.filter(path_mutex -> null != path_mutex._2())
			.forEach(Lambdas.wrap_consumer_i(path_mutex -> path_mutex._2().release()));
	}
	
	/** TODO (ALEPH-12)
	 * @param analytic_bucket
	 * @param job
	 * @return
	 */
	public static boolean isAnalyticJobActive(final ICrudService<AnalyticTriggerStateBean> trigger_crud, final String bucket_name, final String job_name) {
		return false;
	}
	
	/** TODO (ALEPH-12): 
	 * @param trigger_crud
	 * @param triggers
	 */
	public static void storeOrUpdateTriggerStage(final ICrudService<AnalyticTriggerStateBean> trigger_crud, final Map<String, List<AnalyticTriggerStateBean>> triggers) {
		
		triggers.values().stream()
			.flatMap(l -> l.stream())
			.collect(Collectors.groupingBy(v -> Tuples._2T(v.bucket_name(), v.job_name())))
			.entrySet()
			.stream()
			.forEach(kv -> {
				// Step 1: is the bucket/job active?
				final boolean is_active = isAnalyticJobActive(trigger_crud, kv.getKey()._1(), kv.getKey()._2());
				// Step 2: write out the transformed job, with instructions to check in <5s and last_checked == now
				//TODO (ALEPH-12)
				// Step 3: then remove any existing entries with lesser last_checked
				//TODO (ALEPH-12)
			});
			;
	}

	/** TODO (ALEPH-12): 
	 * 
	 */
	public static void updateCompletedJob() {
		// check if there's anything pending, if so copy over, else just unset active
	}
}
