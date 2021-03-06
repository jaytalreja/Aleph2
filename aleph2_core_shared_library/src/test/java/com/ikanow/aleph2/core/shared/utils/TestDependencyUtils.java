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
package com.ikanow.aleph2.core.shared.utils;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.Test;

import scala.Tuple2;

import com.google.common.collect.ImmutableSet;
import com.ikanow.aleph2.core.shared.utils.DependencyUtils.Edge;
import com.ikanow.aleph2.core.shared.utils.DependencyUtils.Node;
import com.ikanow.aleph2.data_model.objects.data_import.EnrichmentControlMetadataBean;
import com.ikanow.aleph2.data_model.utils.BeanTemplateUtils;
import com.ikanow.aleph2.data_model.utils.Tuples;

import fj.data.Validation;

public class TestDependencyUtils {

	//////////////////////////////////////////////
	
	// DEPDENDENCY UTILS
	
	@Test
	public void test_createDepGraph() {
		
		final Set<String> inputs = ImmutableSet.of("$inputs", "test_in1", "test_in2");
		
		final List<EnrichmentControlMetadataBean> enrichers = Arrays.asList(
				BeanTemplateUtils.build(EnrichmentControlMetadataBean.class)
					.with(EnrichmentControlMetadataBean::name, "test_e1")
					.with(EnrichmentControlMetadataBean::dependencies, Arrays.asList("test_in1"))
				.done().get()
				,
				BeanTemplateUtils.build(EnrichmentControlMetadataBean.class)
					.with(EnrichmentControlMetadataBean::name, "test_e2")
					.with(EnrichmentControlMetadataBean::dependencies, Arrays.asList("$previous", "$inputs"))
				.done().get()
				,
				BeanTemplateUtils.build(EnrichmentControlMetadataBean.class)
					.with(EnrichmentControlMetadataBean::name, "test_e3")
					.with(EnrichmentControlMetadataBean::dependencies, Arrays.asList("test_e2", "test_in2", "test_e4"))
				.done().get()
				,
				BeanTemplateUtils.build(EnrichmentControlMetadataBean.class)
					.with(EnrichmentControlMetadataBean::name, "test_e4")
				.done().get()				
				);
		
		final List<EnrichmentControlMetadataBean> fixed_enrichers = DependencyUtils.tidyUpEnrichmentPipeline(enrichers);

		assertEquals("test_in1 // test_e1;$inputs // test_e2;test_in2;test_e4 // $inputs",
				fixed_enrichers.stream().map(e -> e.dependencies().stream().collect(Collectors.joining(";"))).collect(Collectors.joining(" // "))
				);
		
		final List<Node> nodes = DependencyUtils.createDependencyGraph(inputs, fixed_enrichers);

		assertEquals("$inputs IN:  OUT:$inputs->test_e2,$inputs->test_e4 // test_in1 IN:  OUT:test_in1->test_e1 // test_in2 IN:  OUT:test_in2->test_e3 // test_e1 IN: test_in1->test_e1 OUT:test_e1->test_e2 // test_e2 IN: $inputs->test_e2,test_e1->test_e2 OUT:test_e2->test_e3 // test_e3 IN: test_e2->test_e3,test_e4->test_e3,test_in2->test_e3 OUT: // test_e4 IN: $inputs->test_e4 OUT:test_e4->test_e3",
				nodes.stream().map(n -> n.toString() + " IN: " + 
						n.inEdges.stream().map(e -> e.toString()).sorted().collect(Collectors.joining(","))
						+ " OUT:" + 
						n.outEdges.stream().map(e -> e.toString()).sorted().collect(Collectors.joining(","))
						).collect(Collectors.joining(" // "))
				);
		
		final Validation<String, List<Node>> res = DependencyUtils.generateOrder(nodes);
		assertTrue("Should work: " + res.validation(fail -> fail, l -> ""), res.isSuccess());
		
		final LinkedHashMap<String, Tuple2<Set<String>, List<EnrichmentControlMetadataBean>>> containers = 
				DependencyUtils.buildPipelineOfContainers(res.success(), fixed_enrichers.stream().collect(Collectors.toMap(e->e.name(), e->e)));
		
		
		final LinkedHashMap<String, Tuple2<Set<String>, Set<Map<String, Object>>>> displayable_containers = 
				containers.entrySet().stream()
					.collect(Collectors.toMap(
							kv -> kv.getKey(), 
							kv -> Tuples._2T(kv.getValue()._1(), kv.getValue()._2().stream().map(e -> BeanTemplateUtils.toMap(e)).collect(Collectors.toSet())),
							(v1, v2) -> v1,
							() -> new LinkedHashMap<String, Tuple2<Set<String>, Set<Map<String, Object>>>>()
							));
		
		assertEquals("{$inputs=([],[]), test_in1=([],[]), test_in2=([],[]), test_e4=([$inputs],[{name=test_e4, dependencies=[$inputs]}]), test_e1=([test_in1],[{name=test_e1, dependencies=[test_in1]}]), test_e2=([test_e1, $inputs],[{name=test_e2, dependencies=[test_e1, $inputs]}]), test_e3=([test_e2, test_in2, test_e4],[{name=test_e3, dependencies=[test_e2, test_in2, test_e4]}])}",
				displayable_containers.toString()
				);
		
		final Validation<String, LinkedHashMap<String, Tuple2<Set<String>, List<EnrichmentControlMetadataBean>>>> containers_end2end = 
				DependencyUtils.buildPipelineOfContainers(ImmutableSet.of("test_in1", "test_in2"), enrichers);
		
		assertTrue(containers_end2end.isSuccess());

		final LinkedHashMap<String, Tuple2<Set<String>, Set<Map<String, Object>>>> displayable_containers_end2end = 
				containers_end2end.success().entrySet().stream()
					.collect(Collectors.toMap(
							kv -> kv.getKey(), 
							kv -> Tuples._2T(kv.getValue()._1(), kv.getValue()._2().stream().map(e -> BeanTemplateUtils.toMap(e)).collect(Collectors.toSet())),
							(v1, v2) -> v1,
							() -> new LinkedHashMap<String, Tuple2<Set<String>, Set<Map<String, Object>>>>()
							));
		
		assertEquals(displayable_containers.toString(), displayable_containers_end2end.toString());
	}
	
	@Test
	public void test_buildPipelineOfContainers() {
		
		// Similar to above but focusing on coverage instead of black box testing of the components
		
		final List<EnrichmentControlMetadataBean> enrichers = Arrays.asList(
				BeanTemplateUtils.build(EnrichmentControlMetadataBean.class)
					.with(EnrichmentControlMetadataBean::name, "e1")
				.done().get()
				,
				BeanTemplateUtils.build(EnrichmentControlMetadataBean.class)
					.with(EnrichmentControlMetadataBean::name, "e2")
					.with(EnrichmentControlMetadataBean::dependencies, Arrays.asList("$previous"))
				.done().get()
				,
				BeanTemplateUtils.build(EnrichmentControlMetadataBean.class)
					.with(EnrichmentControlMetadataBean::name, "e3")
					.with(EnrichmentControlMetadataBean::grouping_fields, Arrays.asList("?"))
					.with(EnrichmentControlMetadataBean::dependencies, Arrays.asList("$previous"))
				.done().get()
				,
				BeanTemplateUtils.build(EnrichmentControlMetadataBean.class)
					.with(EnrichmentControlMetadataBean::name, "e4")
					.with(EnrichmentControlMetadataBean::dependencies, Arrays.asList("$previous"))
				.done().get()
				,
				BeanTemplateUtils.build(EnrichmentControlMetadataBean.class)
					.with(EnrichmentControlMetadataBean::name, "e5")
					.with(EnrichmentControlMetadataBean::dependencies, Arrays.asList("$previous"))
				.done().get()
				,
				BeanTemplateUtils.build(EnrichmentControlMetadataBean.class)
					.with(EnrichmentControlMetadataBean::name, "e6")
					.with(EnrichmentControlMetadataBean::dependencies, Arrays.asList("e5"))
				.done().get()
				,
				BeanTemplateUtils.build(EnrichmentControlMetadataBean.class)
					.with(EnrichmentControlMetadataBean::name, "e7")
					.with(EnrichmentControlMetadataBean::dependencies, Arrays.asList("e5"))
				.done().get()
				);
		
		final Validation<String, LinkedHashMap<String, Tuple2<Set<String>, List<EnrichmentControlMetadataBean>>>> containers_end2end = 
				DependencyUtils.buildPipelineOfContainers(ImmutableSet.of("test_in1", "test_in2"), enrichers);
		
		assertTrue(containers_end2end.isSuccess());

		final String displayable_containers_end2end = 
				containers_end2end.success().values().stream().distinct()
							.map(v -> Tuples._2T(v._1(), v._2().stream().map(e -> BeanTemplateUtils.toMap(e)).collect(Collectors.toList())))
							.map(t2 -> t2.toString())
							.collect(Collectors.joining(" // "))
							;

		/**/
		System.out.println(displayable_containers_end2end);

		assertEquals(5, containers_end2end.success().values().stream().distinct().count());
		
		final String expected =
				"([],[]) // ([$inputs],[{name=e1, dependencies=[$inputs]}, {name=e2, dependencies=[e1]}]) // ([e2],[{name=e3, dependencies=[e2], grouping_fields=[?]}, {name=e4, dependencies=[e3]}, {name=e5, dependencies=[e4]}]) // ([e5],[{name=e6, dependencies=[e5]}]) // ([e5],[{name=e7, dependencies=[e5]}])";

		assertEquals(expected, displayable_containers_end2end);
	}
	
	//////////////////////////////////////////////
	
	// GRAPH UTILS
	
	@Test
	public void test_topSort_success() {
		new DependencyUtils(); // test coverage!

		{
			final Node seven = new Node("7");
			final Node five = new Node("5");
			final Node five_b = new Node("5");
			final Node seven_b = new Node("7");

			assertFalse(seven.equals(five));
			assertTrue(seven.equals(seven_b));
			
			final Edge e1 = new Edge(five, seven); 
			final Edge e1_b = new Edge(five, seven); 
			final Edge e2 = new Edge(five, seven_b);
			final Edge e3 = new Edge(five_b, seven);
			final Edge e4 = new Edge(five_b, seven_b);
			
			assertFalse(e1.equals(e2)); // (not the same unless it's the same object)
			assertFalse(e1.equals(e3)); 
			assertFalse(e1.equals(e4)); 
			assertTrue(e1_b.equals(e1));
		}
		{
			final Node seven = new Node("7");
			final Node five = new Node("5");
			final Node three = new Node("3");
			final Node eleven = new Node("11");
			final Node eight = new Node("8");
			final Node two = new Node("2");
			final Node nine = new Node("9");
			final Node ten = new Node("10");
		    seven.addEdge(eleven).addEdge(eight);
		    five.addEdge(eleven);
		    three.addEdge(eight).addEdge(ten);
		    eleven.addEdge(two).addEdge(nine).addEdge(ten);
		    eight.addEdge(nine).addEdge(ten);
			
		    final List<Node> in = Arrays.asList(seven, five, three, eleven, eight, two, nine, ten);
		    
		    assertEquals("[3, 5, 7, 11, 2, 8, 9, 10]", Arrays.toString(DependencyUtils.generateOrder(in).success().toArray()));
		}
		// Check the result is deterministic
		{
			final Node seven = new Node("7");
			final Node five = new Node("5");
			final Node three = new Node("3");
			final Node eleven = new Node("11");
			final Node eight = new Node("8");
			final Node two = new Node("2");
			final Node nine = new Node("9");
			final Node ten = new Node("10");
		    seven.addEdge(eleven).addEdge(eight);
		    five.addEdge(eleven);
		    three.addEdge(eight).addEdge(ten);
		    eleven.addEdge(two).addEdge(nine).addEdge(ten);
		    eight.addEdge(nine).addEdge(ten);
			
		    final List<Node> in = Arrays.asList(seven, five, three, eleven, eight, two, nine, ten);
		    
		    assertEquals("[3, 5, 7, 11, 2, 8, 9, 10]", Arrays.toString(DependencyUtils.generateOrder(in).success().toArray()));
		}
	}
	
	@Test
	public void test_topSort_cycle() {
		final Node seven = new Node("7");
		final Node five = new Node("5");
		final Node three = new Node("3");
		final Node eleven = new Node("11");
		final Node eight = new Node("8");
		final Node two = new Node("2");
		final Node nine = new Node("9");
		final Node ten = new Node("10");
	    seven.addEdge(eleven).addEdge(eight);
	    five.addEdge(eleven);
	    three.addEdge(eight).addEdge(ten);
	    eleven.addEdge(two).addEdge(nine).addEdge(ten);
	    eight.addEdge(nine).addEdge(ten).addEdge(seven); // (uh oh, cycle)
		
	    final List<Node> in = Arrays.asList(seven, five, three, eleven, eight, two, nine, ten);
	    
	    assertTrue("Cycle found", DependencyUtils.generateOrder(in).isFail());
	    
	}
	
}
