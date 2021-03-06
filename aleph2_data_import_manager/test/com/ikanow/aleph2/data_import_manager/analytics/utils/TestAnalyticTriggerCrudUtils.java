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
package com.ikanow.aleph2.data_import_manager.analytics.utils;

import static org.junit.Assert.*;

import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.Before;
import org.junit.Test;

import scala.Tuple2;

import com.google.common.collect.ImmutableMap;
import com.ikanow.aleph2.data_model.interfaces.shared_services.ICrudService;
import com.ikanow.aleph2.data_model.objects.data_analytics.AnalyticThreadBean;
import com.ikanow.aleph2.data_model.objects.data_analytics.AnalyticThreadJobBean;
import com.ikanow.aleph2.data_model.objects.data_analytics.AnalyticThreadStateBean;
import com.ikanow.aleph2.data_model.objects.data_analytics.AnalyticThreadTriggerBean;
import com.ikanow.aleph2.data_model.objects.data_analytics.AnalyticThreadTriggerBean.AnalyticThreadComplexTriggerBean;
import com.ikanow.aleph2.data_model.objects.data_analytics.AnalyticThreadTriggerBean.AnalyticThreadComplexTriggerBean.TriggerOperator;
import com.ikanow.aleph2.data_model.objects.data_analytics.AnalyticThreadTriggerBean.AnalyticThreadComplexTriggerBean.TriggerType;
import com.ikanow.aleph2.data_model.objects.data_import.DataBucketBean;
import com.ikanow.aleph2.data_model.objects.data_import.DataBucketStatusBean;
import com.ikanow.aleph2.data_model.utils.BeanTemplateUtils;
import com.ikanow.aleph2.data_model.utils.CrudUtils;
import com.ikanow.aleph2.data_model.utils.Optionals;
import com.ikanow.aleph2.data_model.utils.Tuples;
import com.ikanow.aleph2.management_db.data_model.AnalyticTriggerStateBean;
import com.ikanow.aleph2.management_db.data_model.BucketActionMessage;
import com.ikanow.aleph2.management_db.data_model.BucketActionMessage.BucketActionAnalyticJobMessage.JobMessageType;
import com.ikanow.aleph2.shared.crud.mongodb.services.MockMongoDbCrudServiceFactory;

public class TestAnalyticTriggerCrudUtils {

	ICrudService<AnalyticTriggerStateBean> _test_crud;
	ICrudService<DataBucketStatusBean> _test_status;
	
	@Before
	public void setup() throws InterruptedException, ExecutionException {
		
		MockMongoDbCrudServiceFactory factory = new MockMongoDbCrudServiceFactory();
		_test_crud = factory.getMongoDbCrudService(AnalyticTriggerStateBean.class, String.class, factory.getMongoDbCollection("test.trigger_crud"), Optional.empty(), Optional.empty(), Optional.empty());
		_test_crud.deleteDatastore().get();

		_test_status = factory.getMongoDbCrudService(DataBucketStatusBean.class, String.class, factory.getMongoDbCollection("test.bucket_status"), Optional.empty(), Optional.empty(), Optional.empty());
		_test_status.deleteDatastore().get();
	}	

	@Test
	public void test_storeOrUpdateTriggerStage_relativeCheckTime() throws InterruptedException {
		assertEquals(0, _test_crud.countObjects().join().intValue());
		
		// Same as start to test_storeOrUpdateTriggerStage_updateActivation, except check that the next check isn't scheduled immediately
		
		final DataBucketBean bucket = BeanTemplateUtils.clone(buildBucket("/test/store/trigger", true))
											.with(DataBucketBean::poll_frequency, "2am tomorrow")
										.done();

		// Save a bucket
		{		
			final Stream<AnalyticTriggerStateBean> test_stream = AnalyticTriggerBeanUtils.generateTriggerStateStream(bucket, false, Optional.empty());
			final List<AnalyticTriggerStateBean> test_list = test_stream.collect(Collectors.toList());
			
			System.out.println("Resources = \n" + 
					test_list.stream().map(t -> BeanTemplateUtils.toJson(t).toString()).collect(Collectors.joining("\n")));
			
			assertEquals(8L, test_list.size()); //(8 not 7 cos haven't dedup'd yet)
	
			// 4 internal dependencies
			assertEquals(4L, test_list.stream().filter(t -> null != t.job_name()).count());
			// 4 external dependencies
			assertEquals(4L, test_list.stream().filter(t -> null == t.job_name()).count());
			
			final Map<Tuple2<String, String>, List<AnalyticTriggerStateBean>> grouped_triggers
				= test_list.stream().collect(
						Collectors.groupingBy(t -> Tuples._2T(t.bucket_name(), null)));
			
			AnalyticTriggerCrudUtils.storeOrUpdateTriggerStage(bucket, _test_crud, grouped_triggers).join();
			
			assertEquals(7L, _test_crud.countObjects().join().intValue());
			
			// Only the internal triggers are scheduled for an immediate check
			assertEquals(4L, _test_crud.countObjectsBySpec(
					CrudUtils.allOf(AnalyticTriggerStateBean.class)
						.rangeBelow(AnalyticTriggerStateBean::next_check, new Date(), false)
					).join().intValue());			
		}		
		
	}
	
	
	@Test
	public void test_storeOrUpdateTriggerStage_updateActivation() throws InterruptedException {
		assertEquals(0, _test_crud.countObjects().join().intValue());
		
		final DataBucketBean bucket = buildBucket("/test/store/trigger", true);

		// Save a bucket
		{		
			final Stream<AnalyticTriggerStateBean> test_stream = AnalyticTriggerBeanUtils.generateTriggerStateStream(bucket, false, Optional.empty());
			final List<AnalyticTriggerStateBean> test_list = test_stream.collect(Collectors.toList());
			
			System.out.println("Resources = \n" + 
					test_list.stream().map(t -> BeanTemplateUtils.toJson(t).toString()).collect(Collectors.joining("\n")));
			
			assertEquals(8L, test_list.size()); //(8 not 7 cos haven't dedup'd yet)
	
			// 4 internal dependencies
			assertEquals(4L, test_list.stream().filter(t -> null != t.job_name()).count());
			// 4 external dependencies
			assertEquals(4L, test_list.stream().filter(t -> null == t.job_name()).count());
			
			final Map<Tuple2<String, String>, List<AnalyticTriggerStateBean>> grouped_triggers
				= test_list.stream().collect(
						Collectors.groupingBy(t -> Tuples._2T(t.bucket_name(), null)));
			
			AnalyticTriggerCrudUtils.storeOrUpdateTriggerStage(bucket, _test_crud, grouped_triggers).join();
			
			assertEquals(7L, _test_crud.countObjects().join().intValue());
			
			// Time is relative (default bucket check freq == 2 minutes), so all the triggers should have been set for "now"
			assertEquals(7L, _test_crud.countObjectsBySpec(
					CrudUtils.allOf(AnalyticTriggerStateBean.class)
						.rangeBelow(AnalyticTriggerStateBean::next_check, new Date(), false)
					).join().intValue());
			
		}		
		
		//DEBUG
		//this.printTriggerDatabase();
		
		// Sleep to change times
		Thread.sleep(100L); 
		
		// 2) Modify and update
		final DataBucketBean mod_bucket = 
				BeanTemplateUtils.clone(bucket)
					.with(DataBucketBean::analytic_thread, 
							BeanTemplateUtils.clone(bucket.analytic_thread())
								.with(AnalyticThreadBean::jobs,
										bucket.analytic_thread().jobs().stream()
											.map(j -> 
												BeanTemplateUtils.clone(j)
													.with(AnalyticThreadJobBean::name, "test_" + j.name())
												.done()
											)
											.collect(Collectors.toList())
										)
							.done()
							)
				.done();
		{
			
			final Stream<AnalyticTriggerStateBean> test_stream = AnalyticTriggerBeanUtils.generateTriggerStateStream(mod_bucket, false, Optional.empty());
			final List<AnalyticTriggerStateBean> test_list = test_stream.collect(Collectors.toList());

			final Map<Tuple2<String, String>, List<AnalyticTriggerStateBean>> grouped_triggers
				= test_list.stream().collect(
						Collectors.groupingBy(t -> Tuples._2T(t.bucket_name(), null)));
		
			AnalyticTriggerCrudUtils.storeOrUpdateTriggerStage(bucket, _test_crud, grouped_triggers).join();
		
			//DEBUG
			//this.printTriggerDatabase();
			
			assertEquals(7L, _test_crud.countObjects().join().intValue());
		
			assertEquals(4L, Optionals.streamOf(
					_test_crud.getObjectsBySpec(CrudUtils.allOf(AnalyticTriggerStateBean.class)).join().iterator()
					,
					false)
					.filter(t -> null != t.job_name())
					.filter(t -> t.job_name().startsWith("test_")).count());
		}
		
		// 3) Since we're here might as well try activating...
		{
			final Stream<AnalyticTriggerStateBean> test_stream = Optionals.streamOf(
					_test_crud.getObjectsBySpec(CrudUtils.allOf(AnalyticTriggerStateBean.class)).join().iterator()
					,
					false);
					
			AnalyticTriggerCrudUtils.updateTriggerStatuses(_test_crud, test_stream, new Date(), Optional.of(true)).join();
			
			assertEquals(7L, _test_crud.countObjects().join().intValue());
			assertEquals(7L, Optionals.streamOf(
					_test_crud.getObjectsBySpec(CrudUtils.allOf(AnalyticTriggerStateBean.class)).join().iterator()
					,
					false)
					.filter(t -> t.is_job_active())
					.filter(t -> 100 != Optional.ofNullable(t.last_resource_size()).orElse(-1L))
					.count());
		}		
		// 4) ... and then de-activating...
		{
			final Stream<AnalyticTriggerStateBean> test_stream = Optionals.streamOf(
					_test_crud.getObjectsBySpec(CrudUtils.allOf(AnalyticTriggerStateBean.class)).join().iterator()
					,
					false);
			
			AnalyticTriggerCrudUtils.updateTriggerStatuses(_test_crud, test_stream, new Date(), Optional.of(false)).join();
			
			assertEquals(7L, _test_crud.countObjects().join().intValue());
			assertEquals(7L, Optionals.streamOf(
					_test_crud.getObjectsBySpec(CrudUtils.allOf(AnalyticTriggerStateBean.class)).join().iterator()
					,
					false)
					.filter(t -> !t.is_job_active())
					.filter(t -> 100 != Optional.ofNullable(t.last_resource_size()).orElse(-1L))
					.count());
		}		
		// 5) ... finally re-activate 
		{
			final Stream<AnalyticTriggerStateBean> test_stream = Optionals.streamOf(
					_test_crud.getObjectsBySpec(CrudUtils.allOf(AnalyticTriggerStateBean.class)).join().iterator()
					,
					false)
					.map(t -> BeanTemplateUtils.clone(t)
							.with(AnalyticTriggerStateBean::curr_resource_size, 100L).done())
					;
			
			AnalyticTriggerCrudUtils.updateTriggerStatuses(_test_crud, test_stream, new Date(), Optional.of(true)).join();
			
			assertEquals(7L, _test_crud.countObjects().join().intValue());
			assertEquals(7L, Optionals.streamOf(
					_test_crud.getObjectsBySpec(CrudUtils.allOf(AnalyticTriggerStateBean.class)).join().iterator()
					,
					false)
					.filter(t -> t.is_job_active())
					.filter(t -> 100 == t.last_resource_size())
					.count());
			
		}
	}
	
	//////////////////////////////////////////////////////////////////

	@Test
	public void test_activateUpdateTimesAndSuspend() throws InterruptedException
	{
		assertEquals(0, _test_crud.countObjects().join().intValue());
		
		final DataBucketBean bucket = buildBucket("/test/active/trigger", true);		
		
		// 1) Store as above
		{
			final Stream<AnalyticTriggerStateBean> test_stream = AnalyticTriggerBeanUtils.generateTriggerStateStream(bucket, false, Optional.of("test_host"));
			final List<AnalyticTriggerStateBean> test_list = test_stream.collect(Collectors.toList());
			
			System.out.println("Resources = \n" + 
					test_list.stream().map(t -> BeanTemplateUtils.toJson(t).toString()).collect(Collectors.joining("\n")));
			
			assertEquals(8L, test_list.size());//(8 not 7 because we only dedup at the DB)
	
			// 4 internal dependencies
			assertEquals(4L, test_list.stream().filter(t -> null != t.job_name()).count());
			// 5 external dependencies
			assertEquals(4L, test_list.stream().filter(t -> null == t.job_name()).count());
			
			final Map<Tuple2<String, String>, List<AnalyticTriggerStateBean>> grouped_triggers
				= test_list.stream().collect(
						Collectors.groupingBy(t -> Tuples._2T(t.bucket_name(), null)));
			
			AnalyticTriggerCrudUtils.storeOrUpdateTriggerStage(bucket, _test_crud, grouped_triggers).join();
			
			assertEquals(7L, _test_crud.countObjects().join().intValue());
			
		}
		
		//DEBUG
		//printTriggerDatabase();
		
		// Sleep to change times
		Thread.sleep(100L);
		
		//(activate)
		bucket.analytic_thread().jobs()
								.stream()
								.filter(job -> Optional.ofNullable(job.enabled()).orElse(true))
								.forEach(job -> {
									AnalyticTriggerCrudUtils.createActiveBucketOrJobRecord(_test_crud, bucket, Optional.of(job), Optional.of("test_host"));
								});
		
		// 2) Activate then save suspended - check suspended goes to pending 		
		{
			final Stream<AnalyticTriggerStateBean> test_stream = AnalyticTriggerBeanUtils.generateTriggerStateStream(bucket, true, Optional.of("test_host"));
			final List<AnalyticTriggerStateBean> test_list = test_stream.collect(Collectors.toList());
			
			assertTrue("All suspended", test_list.stream().filter(t -> t.is_bucket_suspended()).findFirst().isPresent());
			
			final Map<Tuple2<String, String>, List<AnalyticTriggerStateBean>> grouped_triggers
			= test_list.stream().collect(
					Collectors.groupingBy(t -> Tuples._2T(t.bucket_name(), null)));
			
			//DEBUG
			//printTriggerDatabase();
			
			assertEquals(13L, _test_crud.countObjects().join().intValue()); // ie 5 active jobs + 1 active bucket, 4 job dependencies, 3 external triggers 			
			
			AnalyticTriggerCrudUtils.storeOrUpdateTriggerStage(bucket, _test_crud, grouped_triggers).join();

			//DEBUG
			//printTriggerDatabase();
			
			assertEquals(17L, _test_crud.countObjects().join().intValue()); // ie 5 active jobs, + 1 active bucket, 4 job dependencies x2 (pending/non-pending), the 3 external triggers get overwritten			
			assertEquals(7L, _test_crud.countObjectsBySpec(
					CrudUtils.allOf(AnalyticTriggerStateBean.class)
						.when(AnalyticTriggerStateBean::is_bucket_suspended, true)
					).join().intValue());			
		}
		
		// Sleep to change times
		Thread.sleep(100L);
		
		// 3) De-activate and check reverts to pending
		{
			AnalyticTriggerCrudUtils.deleteActiveJobEntries(_test_crud, bucket, bucket.analytic_thread().jobs(), Optional.of("test_host")).join();
			
			bucket.analytic_thread().jobs().stream().forEach(job -> {
				//System.out.println("BEFORE: " + job.name() + ": " + _test_crud.countObjects().join().intValue());
				
				AnalyticTriggerCrudUtils.updateCompletedJob(_test_crud, bucket.full_name(), job.name(), Optional.of("test_host")).join();
				
				//System.out.println(" AFTER: " + job.name() + ": " + _test_crud.countObjects().join().intValue());
			});										

			assertEquals(8L, _test_crud.countObjects().join().intValue());			

			assertEquals(7L, _test_crud.countObjectsBySpec(
					CrudUtils.allOf(AnalyticTriggerStateBean.class)
						.when(AnalyticTriggerStateBean::is_pending, false)
					).join().intValue());												
			
			AnalyticTriggerCrudUtils.deleteActiveBucketRecord(_test_crud, bucket.full_name(), Optional.of("test_host")).join();
			
			assertEquals(7L, _test_crud.countObjects().join().intValue());						
		}
	}
	
	//TODO (ALEPH-12): test 2 different locked_to_host, check they don't interfere...

	//TODO: test list
	// (simple)
	// - updateActiveJobTriggerStatus ... Updates active job records' next check times
	// (more complex)
	// - updateTriggerInputsWhenJobOrBucketCompletes
	
	@Test
	public void test_getTriggersToCheck() throws InterruptedException {
		assertEquals(0, _test_crud.countObjects().join().intValue());
		
		final DataBucketBean bucket = buildBucket("/test/check/triggers", true);
		
		// Just set the test up:
		{
			final Stream<AnalyticTriggerStateBean> test_stream = AnalyticTriggerBeanUtils.generateTriggerStateStream(bucket, false, Optional.empty());
			final List<AnalyticTriggerStateBean> test_list = test_stream.collect(Collectors.toList());
			
			assertEquals(8L, test_list.size());//(8 not 7 because we only dedup at the DB)
	
			final Map<Tuple2<String, String>, List<AnalyticTriggerStateBean>> grouped_triggers
				= test_list.stream().collect(
					Collectors.groupingBy(t -> Tuples._2T(t.bucket_name(), null)));
		
			AnalyticTriggerCrudUtils.storeOrUpdateTriggerStage(bucket, _test_crud, grouped_triggers).join();
		
			assertEquals(7L, _test_crud.countObjects().join().intValue());
		}		
		
		// Check the triggers:
		{
			final Map<Tuple2<String, String>, List<AnalyticTriggerStateBean>> res = 
					AnalyticTriggerCrudUtils.getTriggersToCheck(_test_crud).join();
	
			assertEquals("Just one bucket", 1, res.keySet().size());
			
			final List<AnalyticTriggerStateBean> triggers = res.values().stream().findFirst().get();
			assertEquals("One trigger for each resource", 3, triggers.size());
			
			assertTrue("External triggers", triggers.stream().allMatch(trigger -> null != trigger.input_resource_combined()));
			
			// Save the triggers
			
			//DEBUG
			//this.printTriggerDatabase();
			
			AnalyticTriggerCrudUtils.updateTriggerStatuses(_test_crud, triggers.stream(), 
					Date.from(Instant.now().plusSeconds(2)), Optional.empty()
					).join();

			//DEBUG
			//this.printTriggerDatabase();			
		}
		
		// Try again
		{
			final Map<Tuple2<String, String>, List<AnalyticTriggerStateBean>> res = 
					AnalyticTriggerCrudUtils.getTriggersToCheck(_test_crud).join();
	
			assertEquals("None this time", 0, res.keySet().size());
		}		
		
		// Activate the internal jobs and the external triggers, and set the times back
		// (this time will get the job deps but not the triggers)
		
		{
			// activates external with bucket_active
			AnalyticTriggerCrudUtils.updateTriggersWithBucketOrJobActivation(
					_test_crud, bucket, Optional.empty(), Optional.empty()).join();
					
			// activate internal with bucket and job active
			AnalyticTriggerCrudUtils.updateTriggersWithBucketOrJobActivation(
					_test_crud, bucket, Optional.of(bucket.analytic_thread().jobs()), Optional.empty())
					.join();

			//(just update the next trigger time)
			_test_crud.updateObjectsBySpec(CrudUtils.allOf(AnalyticTriggerStateBean.class), 
					Optional.empty(), 
						CrudUtils.update(AnalyticTriggerStateBean.class)
									.set(AnalyticTriggerStateBean::next_check, Date.from(Instant.now().minusSeconds(2)))
					).join();
			
			final Map<Tuple2<String, String>, List<AnalyticTriggerStateBean>> res = 
					AnalyticTriggerCrudUtils.getTriggersToCheck(_test_crud).join();
	
			final List<AnalyticTriggerStateBean> triggers = res.values().stream().findFirst().get();
			assertEquals("One trigger for each job dep", 4, triggers.size());
			
			assertFalse("Should be external triggers", triggers.stream().allMatch(trigger -> null == trigger.job_name()));
			
			AnalyticTriggerCrudUtils.updateTriggerStatuses(_test_crud, triggers.stream(), 
					Date.from(Instant.now().plusSeconds(2)), Optional.empty()
					).join();			
		}
		
		// Try again
		{
			final Map<Tuple2<String, String>, List<AnalyticTriggerStateBean>> res = 
					AnalyticTriggerCrudUtils.getTriggersToCheck(_test_crud).join();
	
			assertEquals("None this time", 0, res.keySet().size());			
		}		
		
		// Activate the jobs "properly"
		
		{
			AnalyticTriggerCrudUtils.createActiveBucketOrJobRecord(
					_test_crud, 
					bucket, 
					Optional.of(bucket.analytic_thread().jobs().stream().findFirst().get()), 
					Optional.empty()
					).join();
			
			//DEBUG
			//this.printTriggerDatabase();
			
			final Map<Tuple2<String, String>, List<AnalyticTriggerStateBean>> res2 = 
					AnalyticTriggerCrudUtils.getTriggersToCheck(_test_crud).join();
	
			assertEquals("Got the one active bucket", 1, res2.keySet().size());			
			
			final List<AnalyticTriggerStateBean> triggers = res2.values().stream().findFirst().get();
			assertEquals("One trigger for the one active job + 1 for the bucket", 2, triggers.size());

		}		
	}
	
	//////////////////////////////////////////////////////////////////
	
	//TODO (ALEPH-12): delete bucket, check clears the DB

	//////////////////////////////////////////////////////////////////
	
	public void printTriggerDatabase()
	{
		List<com.fasterxml.jackson.databind.JsonNode> ll = Optionals.streamOf(_test_crud.getRawService().getObjectsBySpec(CrudUtils.allOf())
				.join().iterator(), true)
				.collect(Collectors.toList())
				;
			System.out.println("DB_Resources = \n" + 
					ll.stream().map(t -> t.toString()).collect(Collectors.joining("\n")));		
	}
	
	/** Generates 4 job->job dependency
	 * @param bucket_path
	 * @param trigger
	 * @return
	 */
	public static DataBucketBean buildBucket(final String bucket_path, boolean trigger) {
		
		final AnalyticThreadJobBean job0 = 
				BeanTemplateUtils.build(AnalyticThreadJobBean.class)
					.with(AnalyticThreadJobBean::name, "job0")
					.with(AnalyticThreadJobBean::inputs, Arrays.asList(
							//(empty input list)
							))
				.done().get();
		
		//#INT1: job1a -> job0
		final AnalyticThreadJobBean job1a = 
				BeanTemplateUtils.build(AnalyticThreadJobBean.class)
					.with(AnalyticThreadJobBean::name, "job1a")
					.with(AnalyticThreadJobBean::inputs, Arrays.asList( // (multiple inputs)
							BeanTemplateUtils.build(AnalyticThreadJobBean.AnalyticThreadJobInputBean.class)
								.with(AnalyticThreadJobBean.AnalyticThreadJobInputBean::resource_name_or_id, "/test_job1a_input_1")
								.with(AnalyticThreadJobBean.AnalyticThreadJobInputBean::data_service, "storage_service")
							.done().get()
							,
							BeanTemplateUtils.build(AnalyticThreadJobBean.AnalyticThreadJobInputBean.class)
							.with(AnalyticThreadJobBean.AnalyticThreadJobInputBean::resource_name_or_id, "/test_job1a_input:temp")
							.with(AnalyticThreadJobBean.AnalyticThreadJobInputBean::data_service, "batch")
						.done().get()
							))
					.with(AnalyticThreadJobBean::dependencies, Arrays.asList("job0"))
				.done().get();
		
		//#INT2: job1b -> job0
		final AnalyticThreadJobBean job1b = 
				BeanTemplateUtils.build(AnalyticThreadJobBean.class)
					.with(AnalyticThreadJobBean::name, "job1b")
					//(no input)
					.with(AnalyticThreadJobBean::dependencies, Arrays.asList("job0"))
				.done().get();
		
		//#INT3: job2 -> job1a
		//#INT4: job2 -> job1b
		final AnalyticThreadJobBean job2 = 
				BeanTemplateUtils.build(AnalyticThreadJobBean.class)
					.with(AnalyticThreadJobBean::name, "job2")
					.with(AnalyticThreadJobBean::dependencies, Arrays.asList("job1a", "job1b"))
				.done().get();
		
		//Will be ignored as has no deps:
		final AnalyticThreadJobBean job3 = 
				BeanTemplateUtils.build(AnalyticThreadJobBean.class)
					.with(AnalyticThreadJobBean::name, "job3")
					.with(AnalyticThreadJobBean::inputs, Arrays.asList( // (single input)
							BeanTemplateUtils.build(AnalyticThreadJobBean.AnalyticThreadJobInputBean.class)
								.with(AnalyticThreadJobBean.AnalyticThreadJobInputBean::resource_name_or_id, "/test_job3_input_1")
								.with(AnalyticThreadJobBean.AnalyticThreadJobInputBean::data_service, "search_index_service")
							.done().get()							
							))
				.done().get();

		//Will be ignored as is not enabled:
		final AnalyticThreadJobBean job4 = 
				BeanTemplateUtils.build(AnalyticThreadJobBean.class)
					.with(AnalyticThreadJobBean::name, "job4")
					.with(AnalyticThreadJobBean::enabled, false)
					.with(AnalyticThreadJobBean::dependencies, Arrays.asList("job0"))
					.with(AnalyticThreadJobBean::inputs, Arrays.asList( // (single input)
							
							))
				.done().get();

		final AnalyticThreadTriggerBean trigger_info =
			BeanTemplateUtils.build(AnalyticThreadTriggerBean.class)
				.with(AnalyticThreadTriggerBean::auto_calculate, trigger ? null : true)
				.with(AnalyticThreadTriggerBean::trigger, trigger ? buildTrigger() : null)
			.done().get();
		
		final AnalyticThreadBean thread = 
				BeanTemplateUtils.build(AnalyticThreadBean.class)
					.with(AnalyticThreadBean::jobs, Arrays.asList(job0, job1a, job1b, job2, job3, job4))
					.with(AnalyticThreadBean::trigger_config, trigger_info)
				.done().get();
		
		final DataBucketBean bucket =
				BeanTemplateUtils.build(DataBucketBean.class)
					.with(DataBucketBean::_id, bucket_path.replace("/", "_"))
					.with(DataBucketBean::full_name, bucket_path)
					.with(DataBucketBean::analytic_thread, thread)
				.done().get();				
		
		return bucket;
	}
	
	/** Generates 3 triggers (add a 4th)
	 * @return
	 */
	public static AnalyticThreadComplexTriggerBean buildTrigger() {
		
		final AnalyticThreadComplexTriggerBean complex_trigger =
				BeanTemplateUtils.build(AnalyticThreadComplexTriggerBean.class)
					.with(AnalyticThreadComplexTriggerBean::op, TriggerOperator.and)
					.with(AnalyticThreadComplexTriggerBean::dependency_list, Arrays.asList(
						//1
						BeanTemplateUtils.build(AnalyticThreadComplexTriggerBean.class)
							.with(AnalyticThreadComplexTriggerBean::op, TriggerOperator.or)							
							.with(AnalyticThreadComplexTriggerBean::dependency_list, Arrays.asList(
									// OR#1
									BeanTemplateUtils.build(AnalyticThreadComplexTriggerBean.class)
										.with(AnalyticThreadComplexTriggerBean::op, TriggerOperator.and)
										.with(AnalyticThreadComplexTriggerBean::dependency_list, Arrays.asList(
												// #EXT1: input_resource_name_or_id:"/input/test/1/1/1", input_data_service:"search_index_service"
												BeanTemplateUtils.build(AnalyticThreadComplexTriggerBean.class)
													.with(AnalyticThreadComplexTriggerBean::enabled, true)
													.with(AnalyticThreadComplexTriggerBean::type, TriggerType.bucket)
													.with(AnalyticThreadComplexTriggerBean::data_service, "search_index_service")
													.with(AnalyticThreadComplexTriggerBean::resource_name_or_id, "/input/test/1/1/1")
													.with(AnalyticThreadComplexTriggerBean::resource_trigger_limit, 1000L)
												.done().get()
												,
												BeanTemplateUtils.build(AnalyticThreadComplexTriggerBean.class)
													.with(AnalyticThreadComplexTriggerBean::enabled, true)
													.with(AnalyticThreadComplexTriggerBean::op, TriggerOperator.and)
													.with(AnalyticThreadComplexTriggerBean::dependency_list, Arrays.asList(
															// #EXT2: input_resource_name_or_id:"/input/test/1/1/2", input_data_service:"storage_service"
															BeanTemplateUtils.build(AnalyticThreadComplexTriggerBean.class)
																.with(AnalyticThreadComplexTriggerBean::enabled, true)
																.with(AnalyticThreadComplexTriggerBean::type, TriggerType.bucket)
																.with(AnalyticThreadComplexTriggerBean::data_service, "storage_service")
																.with(AnalyticThreadComplexTriggerBean::resource_name_or_id, "/input/test/1/1/2")
																.with(AnalyticThreadComplexTriggerBean::resource_trigger_limit, 1000L)
															.done().get()
															,
															BeanTemplateUtils.build(AnalyticThreadComplexTriggerBean.class)
																.with(AnalyticThreadComplexTriggerBean::enabled, false)
																.with(AnalyticThreadComplexTriggerBean::type, TriggerType.file)
																.with(AnalyticThreadComplexTriggerBean::resource_name_or_id, "/input/test/1/1/3")
																.with(AnalyticThreadComplexTriggerBean::resource_trigger_limit, 1000L)
															.done().get()
															,
															BeanTemplateUtils.build(AnalyticThreadComplexTriggerBean.class) //////////PURE DUP
																.with(AnalyticThreadComplexTriggerBean::enabled, true)
																.with(AnalyticThreadComplexTriggerBean::type, TriggerType.bucket)
																.with(AnalyticThreadComplexTriggerBean::data_service, "search_index_service")
																.with(AnalyticThreadComplexTriggerBean::resource_name_or_id, "/input/test/1/1/1")
																.with(AnalyticThreadComplexTriggerBean::resource_trigger_limit, 1000L)
															.done().get()
													))
												.done().get()
										))
									.done().get()
//Add another one here									
//									,
//									BeanTemplateUtils.build(AnalyticThreadComplexTriggerBean.class)
//									.done().get()
							))
						.done().get()
						,
						//2
						BeanTemplateUtils.build(AnalyticThreadComplexTriggerBean.class)
							.with(AnalyticThreadComplexTriggerBean::enabled, true)
							.with(AnalyticThreadComplexTriggerBean::op, TriggerOperator.not)
							.with(AnalyticThreadComplexTriggerBean::dependency_list, Arrays.asList(
									// #EXT3: "input_resource_name_or_id":"/input/test/1/1/1", no data_service
									BeanTemplateUtils.build(AnalyticThreadComplexTriggerBean.class)////////////DUP WITH DIFF DATA SERVICE
										.with(AnalyticThreadComplexTriggerBean::enabled, true)
										.with(AnalyticThreadComplexTriggerBean::type, TriggerType.bucket)
										.with(AnalyticThreadComplexTriggerBean::resource_name_or_id, "/input/test/1/1/1")
									.done().get()
									,
									BeanTemplateUtils.build(AnalyticThreadComplexTriggerBean.class)
										.with(AnalyticThreadComplexTriggerBean::enabled, false)
										.with(AnalyticThreadComplexTriggerBean::type, TriggerType.bucket)
										.with(AnalyticThreadComplexTriggerBean::resource_name_or_id, "/input/test/2/1:test")
									.done().get()
							))
						.done().get()
						,
						// 3
						BeanTemplateUtils.build(AnalyticThreadComplexTriggerBean.class)
							.with(AnalyticThreadComplexTriggerBean::enabled, false)///////////DISABLED
							.with(AnalyticThreadComplexTriggerBean::op, TriggerOperator.and)
							.with(AnalyticThreadComplexTriggerBean::dependency_list, Arrays.asList(
									BeanTemplateUtils.build(AnalyticThreadComplexTriggerBean.class)
										.with(AnalyticThreadComplexTriggerBean::enabled, true)
										.with(AnalyticThreadComplexTriggerBean::type, TriggerType.file)
										.with(AnalyticThreadComplexTriggerBean::data_service, "storage_service")
										.with(AnalyticThreadComplexTriggerBean::resource_name_or_id, "/input/test/3/1")
										.with(AnalyticThreadComplexTriggerBean::resource_trigger_limit, 1000L)
									.done().get()
									,
									BeanTemplateUtils.build(AnalyticThreadComplexTriggerBean.class)
										.with(AnalyticThreadComplexTriggerBean::enabled, true)
										.with(AnalyticThreadComplexTriggerBean::op, TriggerOperator.and)
										.with(AnalyticThreadComplexTriggerBean::dependency_list, Arrays.asList(
												BeanTemplateUtils.build(AnalyticThreadComplexTriggerBean.class)
													.with(AnalyticThreadComplexTriggerBean::enabled, true)
													.with(AnalyticThreadComplexTriggerBean::type, TriggerType.file)
													.with(AnalyticThreadComplexTriggerBean::data_service, "storage_service")
													.with(AnalyticThreadComplexTriggerBean::resource_name_or_id, "/input/test/3/2")
													.with(AnalyticThreadComplexTriggerBean::resource_trigger_limit, 1000L)
												.done().get()
										))
									.done().get()
							))
						.done().get()
						))
				.done().get();
		
		return complex_trigger;
	}

	@Test
	public void test_updateAnalyticThreadState() {
		
		// Most of the functionality testing for this is provided by TestAnalyticsTriggerWorkerActor.test_jobTriggerScenario
		// (including the internal/external job trigger case, which is the most complex one)
		// Here we're just going to cover a few edge cases
		
		// 1) If we're running a test bucket then it just always immediately returns
		{
			final DataBucketBean test_bucket = BeanTemplateUtils.build(DataBucketBean.class)
						.with(DataBucketBean::full_name, "/aleph2_testing/alex/random/test")
					.done().get();
			
			// (all the params just get bypassed)
			assertEquals(true, AnalyticTriggerCrudUtils.updateAnalyticThreadState(null, test_bucket, null, null).join());
		}
		
		final DataBucketBean normal_bucket = BeanTemplateUtils.build(DataBucketBean.class)
				.with(DataBucketBean::_id, "_random_test")
				.with(DataBucketBean::full_name, "/random/test")
			.done().get();
		
		final AnalyticThreadJobBean test_job = BeanTemplateUtils.build(AnalyticThreadJobBean.class)
				.with(AnalyticThreadJobBean::name, "test_job")
			.done().get();
				
		final AnalyticThreadJobBean test_job_2 = BeanTemplateUtils.build(AnalyticThreadJobBean.class)
				.with(AnalyticThreadJobBean::name, "test_job_2")
			.done().get();
		
		// 2) Next up... if the status beans aren't present then should return false in all different cases
		{
			// 2a) Bucket starting
			{
				final BucketActionMessage new_message = 
						AnalyticTriggerBeanUtils.buildInternalEventMessage(normal_bucket, null, JobMessageType.starting, Optional.empty());		
				
				assertEquals(false, AnalyticTriggerCrudUtils.updateAnalyticThreadState(new_message, normal_bucket, _test_status, Optional.of(new Date())).join());
				
				assertEquals(false, AnalyticTriggerCrudUtils.updateAnalyticThreadState(new_message, normal_bucket, _test_status, Optional.empty()).join());
			}
			// 2b) Bucket stopping
			{
				final BucketActionMessage new_message = 
						AnalyticTriggerBeanUtils.buildInternalEventMessage(normal_bucket, null, JobMessageType.stopping, Optional.empty());		
				
				assertEquals(false, AnalyticTriggerCrudUtils.updateAnalyticThreadState(new_message, normal_bucket, _test_status, Optional.of(new Date())).join());
				
				assertEquals(false, AnalyticTriggerCrudUtils.updateAnalyticThreadState(new_message, normal_bucket, _test_status, Optional.of(new Date())).join());
			}
			// 2c) Jobs starting
			{
				final BucketActionMessage new_message = 
						AnalyticTriggerBeanUtils.buildInternalEventMessage(normal_bucket, Arrays.asList(test_job), JobMessageType.starting, Optional.empty());		
				
				assertEquals(false, AnalyticTriggerCrudUtils.updateAnalyticThreadState(new_message, normal_bucket, _test_status, Optional.of(new Date())).join());
				
				assertEquals(false, AnalyticTriggerCrudUtils.updateAnalyticThreadState(new_message, normal_bucket, _test_status, Optional.empty()).join());
			}
			// 2d) Jobs stopping
			{
				final BucketActionMessage new_message = 
						AnalyticTriggerBeanUtils.buildInternalEventMessage(normal_bucket, Arrays.asList(test_job), JobMessageType.stopping, Optional.empty());		
				
				assertEquals(false, AnalyticTriggerCrudUtils.updateAnalyticThreadState(new_message, normal_bucket, _test_status, Optional.of(new Date())).join());
				
				assertEquals(false, AnalyticTriggerCrudUtils.updateAnalyticThreadState(new_message, normal_bucket, _test_status, Optional.empty()).join());
			}
		}
		
		// 3) Finally (this is the most complex _edge_ case), handle the case where some of the jobs have already been started buth others haven't
		//    This one requires a decent amount of set up
		{
			final DataBucketStatusBean normal_bucket_status = BeanTemplateUtils.build(DataBucketStatusBean.class)
					.with(DataBucketStatusBean::_id, "_random_test")
					.with(DataBucketStatusBean::bucket_path, "/random/test")
					.with(DataBucketStatusBean::global_analytic_state,
							BeanTemplateUtils.build(AnalyticThreadStateBean.class)
								.with(AnalyticThreadStateBean::curr_run, new Date(0L))
							.done().get()
							)
					.with(DataBucketStatusBean::analytic_state,
							new LinkedHashMap<String, AnalyticThreadStateBean>(
									ImmutableMap.of(
											"test_job", 
											BeanTemplateUtils.build(AnalyticThreadStateBean.class)
												.with(AnalyticThreadStateBean::curr_run, new Date(0L))
											.done().get(),
											"test_job_2", 
											BeanTemplateUtils.build(AnalyticThreadStateBean.class)
											.done().get()
											)
									)
							)
				.done().get();
			
			// Jobs where 1 gets changed, the other doesn't
			{
				_test_status.storeObject(normal_bucket_status, true).join();
				
				final BucketActionMessage new_message = 
						AnalyticTriggerBeanUtils.buildInternalEventMessage(normal_bucket, Arrays.asList(test_job, test_job_2), JobMessageType.starting, Optional.empty());		
				
				assertEquals(true, AnalyticTriggerCrudUtils.updateAnalyticThreadState(new_message, normal_bucket, _test_status, Optional.empty()).join());
				
				final DataBucketStatusBean normal_bucket_status_res = _test_status.getObjectById("_random_test").join().get();
				
				assertEquals(0L, normal_bucket_status_res.analytic_state().get("test_job").curr_run().getTime());
				assertTrue(normal_bucket_status_res.analytic_state().get("test_job_2").curr_run().getTime() > 1L); // (ie now!)
			}
			// Jobs where the only one specified is changed, won't update anything
			{
				_test_status.storeObject(normal_bucket_status, true).join();
				
				final BucketActionMessage new_message = 
						AnalyticTriggerBeanUtils.buildInternalEventMessage(normal_bucket, Arrays.asList(test_job), JobMessageType.starting, Optional.empty());		
				
				assertEquals(true, AnalyticTriggerCrudUtils.updateAnalyticThreadState(new_message, normal_bucket, _test_status, Optional.empty()).join());
				
				final DataBucketStatusBean normal_bucket_status_res = _test_status.getObjectById("_random_test").join().get();
				
				assertEquals(0L, normal_bucket_status_res.analytic_state().get("test_job").curr_run().getTime());				
			}
			// Bucket
			{
				_test_status.storeObject(normal_bucket_status, true).join();
				
				final BucketActionMessage new_message = 
						AnalyticTriggerBeanUtils.buildInternalEventMessage(normal_bucket, null, JobMessageType.starting, Optional.empty());		
				
				assertEquals(true, AnalyticTriggerCrudUtils.updateAnalyticThreadState(new_message, normal_bucket, _test_status, Optional.empty()).join());
				
				final DataBucketStatusBean normal_bucket_status_res = _test_status.getObjectById("_random_test").join().get();
				
				assertEquals(0L, normal_bucket_status_res.global_analytic_state().curr_run().getTime());				
			}
		}
	}
}
