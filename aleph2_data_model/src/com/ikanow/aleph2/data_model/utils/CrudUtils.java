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
package com.ikanow.aleph2.data_model.utils;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

import org.checkerframework.checker.nullness.qual.NonNull;

import scala.Tuple2;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Multimap;
import com.google.common.collect.LinkedHashMultimap;
import com.ikanow.aleph2.data_model.utils.BeanTemplateUtils.BeanTemplate;
import com.ikanow.aleph2.data_model.utils.BeanTemplateUtils.MethodNamingHelper;

/** Very simple set of query builder utilties
 * @author acp
 */
public class CrudUtils {

	///////////////////////////////////////////////////////////////////
	///////////////////////////////////////////////////////////////////
	
	// CLASS INTERFACES
	
	/** Just an empty parent of SingleQueryComponent (SingleBeanQueryComponent, SingleJsonQueryComponent) and MultiQueryComponent
	 * @author acp
	 *
	 * @param <T>
	 */
	public static abstract class QueryComponent<T> {
		private QueryComponent() {}
		
		// Public interface - read
		// THIS IS FOR CRUD INTERFACE IMPLEMENTERS ONLY
		
		public abstract Operator getOp();
		
		public abstract Long getLimit();

		public abstract List<Tuple2<String, Integer>> getOrderBy();
	}

	// Operators for querying
	public enum Operator { all_of, any_of, exists, range_open_open, range_closed_open, range_closed_closed, range_open_closed, equals };
	
	///////////////////////////////////////////////////////////////////
	
	public static abstract class UpdateComponent<T> {
		// Just an empty parent of SingleQueryComponent and MultiQueryComponent
		private UpdateComponent() {}
		
		/** Returns the class type of the update component
		 * @return the Class of the component
		 */
		@NonNull
		public abstract Class<T> getElementClass();
		
		/** All update elements  
		 * @return the list of all update elements  
		 */
		@NonNull
		public abstract LinkedHashMultimap<String, Tuple2<UpdateOperator, Object>> getAll();
	}

	// Operators for updating
	public enum UpdateOperator { increment, set, unset, add, remove, add_deduplicate }
	
	///////////////////////////////////////////////////////////////////
	///////////////////////////////////////////////////////////////////
	
	// FUNCTION INTERFACES
	
	/** Returns a query component where all of the fields in t (together with other fields added using withAny/withAll) must match
	 *  Returns a type safe version that can be used in the raw JSON CRUD services
	 * @param clazz - the class of the template
	 * @return the query component "helper"
	 */
	@NonNull
	public static <T> SingleJsonQueryComponent<T> allOf_json(final @NonNull Class<T> clazz) {
		return new SingleJsonQueryComponent<T>(BeanTemplateUtils.build(clazz).done(), Operator.all_of);
	}
	
	/** Returns a query component where all of the fields in t (together with other fields added using withAny/withAll) must match
	 *  Returns a type safe version that can be used in the raw JSON CRUD services
	 *  Recommend using the clazz version unless you are generating lots of different queries from a single template
	 * @param t - the starting set of fields (can be empty generated from default c'tor)
	 * @return the query component "helper"
	 */
	@NonNull
	public static <T> SingleJsonQueryComponent<T> allOf_json(final @NonNull BeanTemplate<T> t) {
		return new SingleJsonQueryComponent<T>(t, Operator.all_of);
	}
	/** Returns a query component where any of the fields in t (together with other fields added using withAny/withAll) can match
	 *  Returns a type safe version that can be used in the raw JSON CRUD services
	 * @param clazz - the class of the template
	 * @return the query component "helper"
	 */
	@NonNull
	public static <T> SingleJsonQueryComponent<T> anyOf_json(final @NonNull Class<T> clazz) {
		return new SingleJsonQueryComponent<T>(BeanTemplateUtils.build(clazz).done(), Operator.any_of);
	}
	/** Returns a query component where any of the fields in t (together with other fields added using withAny/withAll) can match
	 *  Returns a type safe version that can be used in the raw JSON CRUD services
	 *  Recommend using the clazz version unless you are generating lots of different queries from a single template
	 * @param t- the starting set of fields (can be empty generated from default c'tor)
	 * @return the query component "helper"
	 */
	@NonNull
	public static <T> SingleJsonQueryComponent<T> anyOf_json(final @NonNull BeanTemplate<T> t) {
		return new SingleJsonQueryComponent<T>(t, Operator.any_of);
	}
	
	/** Converts a bean version of a query across to a JSON one
	 * @param bean_version - the original query component
	 * @return
	 */
	public static <T> QueryComponent<JsonNode> to_json(final @NonNull QueryComponent<T> bean_version) {
		//TODO (ALEPH-22)
		return null;
	}
	
	/** Converts a bean version of an update across to a JSON one
	 * @param bean_version - the original update component
	 * @return
	 */
	public static <T> UpdateComponent<JsonNode> to_json(final @NonNull UpdateComponent<T> bean_version) {
		//TODO (ALEPH-22)
		return null;
	}
	
	/** Converts a bean version of a query across to a JSON one
	 * @param bean_version - the original query component
	 * @return
	 */
	public static <T> QueryComponent<T> from_json(final @NonNull QueryComponent<JsonNode> json_version) {
		//TODO (ALEPH-22)
		return null;
	}
	
	/** Converts a bean version of an update across to a JSON one
	 * @param bean_version - the original update component
	 * @return
	 */
	public static <T> UpdateComponent<T> from_json(final @NonNull UpdateComponent<JsonNode> json_version) {
		//TODO (ALEPH-22)
		return null;
	}
	
	///////////////////////////////////////////////////////////////////
	
	/** Returns a query component where all of the fields added using withAny/withAll/etc must match
	 * @return the query component "helper"
	 */
	@NonNull
	public static SingleQueryComponent<JsonNode> allOf() {
		return new SingleQueryComponent<JsonNode>(null, Operator.all_of);
	}
	
	/** Returns a query component where all of the fields in t (together with other fields added using withAny/withAll) must match
	 * @param clazz - the class of the template
	 * @return the query component "helper"
	 */
	@NonNull
	public static <T> SingleBeanQueryComponent<T> allOf(final @NonNull Class<T> clazz) {
		return new SingleBeanQueryComponent<T>(BeanTemplateUtils.build(clazz).done(), Operator.all_of);
	}
	/** Returns a query component where all of the fields in t (together with other fields added using withAny/withAll) must match
	 *  Recommend using the clazz version unless you are generating lots of different queries from a single template
	 * @param t - the starting set of fields (can be empty generated from default c'tor)
	 * @return the query component "helper"
	 */
	@NonNull
	public static <T> SingleBeanQueryComponent<T> allOf(final @NonNull BeanTemplate<T> t) {
		return new SingleBeanQueryComponent<T>(t, Operator.all_of);
	}
	
	/** Returns a query component where any of the fields added using withAny/withAll/etc can match
	 * @return the query component "helper"
	 */
	@NonNull
	public static SingleQueryComponent<JsonNode> anyOf() {
		return new SingleQueryComponent<JsonNode>(null, Operator.any_of);
	}
	
	/** Returns a query component where any of the fields in t (together with other fields added using withAny/withAll) can match
	 * @param clazz - the class of the template
	 * @return the query component "helper"
	 */
	@NonNull
	public static <T> SingleBeanQueryComponent<T> anyOf(final @NonNull Class<T> clazz) {
		return new SingleBeanQueryComponent<T>(BeanTemplateUtils.build(clazz).done(), Operator.any_of);
	}
	/** Returns a query component where any of the fields in t (together with other fields added using withAny/withAll) can match
	 *  Recommend using the clazz version unless you are generating lots of different queries from a single template
	 * @param t- the starting set of fields (can be empty generated from default c'tor)
	 * @return the query component "helper"
	 */
	@NonNull
	public static <T> SingleBeanQueryComponent<T> anyOf(final @NonNull BeanTemplate<T> t) {
		return new SingleBeanQueryComponent<T>(t, Operator.any_of);
	}
	
	///////////////////////////////////////////////////////////////////
	
	/** Returns a "multi" query component where all of the QueryComponents in the list (and added via andAlso) must match (NOTE: each component *internally* can use ORs or ANDs)
	 * @param components - a list of query components
	 * @return the "multi" query component "helper"
	 */
	@SafeVarargs
	@NonNull
	public static <T> MultiQueryComponent<T> allOf(final @NonNull SingleQueryComponent<T>... components) {
		return new MultiQueryComponent<T>(Operator.all_of, components);
	}
	
	/** Returns a "multi" query component where any of the QueryComponents in the list (and added via andAlso) can match (NOTE: each component *internally* can use ORs or ANDs)
	 * @param components - a list of query components
	 * @return the "multi" query component "helper"
	 */
	@SafeVarargs
	@NonNull
	public static <T> MultiQueryComponent<T> anyOf(final @NonNull SingleQueryComponent<T>... components) {
		return new MultiQueryComponent<T>(Operator.any_of, components);
	}

	///////////////////////////////////////////////////////////////////

	public static <T> BeanUpdateComponent<T> update(@NonNull Class<T> clazz) {
		return new BeanUpdateComponent<T>(BeanTemplateUtils.build(clazz).done());
	}
	
	public static <T> BeanUpdateComponent<T> update(@NonNull BeanTemplate<T> t) {
		return new BeanUpdateComponent<T>(t);
	}
	
	///////////////////////////////////////////////////////////////////
	
	@SuppressWarnings("unchecked")
	public static <T> JsonUpdateComponent<T> update_json(@NonNull Class<T> clazz) {
		return new JsonUpdateComponent<T>((BeanTemplate<Object>) BeanTemplateUtils.build(clazz).done());
	}
	
	@SuppressWarnings("unchecked")
	public static <T> JsonUpdateComponent<T> update_json(@NonNull BeanTemplate<T> t) {
		return new JsonUpdateComponent<T>((BeanTemplate<Object>) t);
	}

	///////////////////////////////////////////////////////////////////
	///////////////////////////////////////////////////////////////////
	
	// CLASS INTERFACES
	
	/** Encapsulates a very set of queries, all ANDed or ORed together
	 * @author acp
	 * @param <T> the bean type being queried
	 */
	public static class MultiQueryComponent<T> extends QueryComponent<T> {

		// Public interface - read
		// THIS IS FOR CRUD INTERFACE IMPLEMENTERS ONLY
		
		/** A list of all the single query elements in a multi query 
		 * @return a list of single query elements
		 */
		@NonNull
		public List<SingleQueryComponent<T>> getElements() {
			return _elements;
		}
		
		/* (non-Javadoc)
		 * @see com.ikanow.aleph2.data_model.utils.CrudUtils.QueryComponent#getOp()
		 */
		@NonNull
		public Operator getOp() {
			return _op;
		}
				
		/* (non-Javadoc)
		 * @see com.ikanow.aleph2.data_model.utils.CrudUtils.QueryComponent#getLimit()
		 */
		@NonNull
		public Long getLimit() {
			return _limit;
		}

		/* (non-Javadoc)
		 * @see com.ikanow.aleph2.data_model.utils.CrudUtils.QueryComponent#getOrderBy()
		 */
		@NonNull
		public List<Tuple2<String, Integer>> getOrderBy() {
			return _orderBy;
		}
		
		// Public interface - build
		
		/** More query components to match, using whichever of all/any that was first described
		 * @param components - more query components
		 * @return the "multi" query component "helper"
		 */
		@SafeVarargs
		@NonNull
		public final MultiQueryComponent<T> also(final @NonNull SingleQueryComponent<T>... components) {
			_elements.addAll(Arrays.asList(components)); 
			return this;
		}
		
		/** Limits the number of returned objects
		 * @param limit - the max number of objects to retrieve
		 * @return the "multi" query component "helper"
		 */
		@NonNull
		public MultiQueryComponent<T> limit(final long limit) {
			_limit = limit;
			return this;
		}
		/** Specifies the order in which objects will be returned
		 * @param orderList - a list of 2-tupes, first is the field string, second is +1 for ascending, -1 for descending
		 * @return the "multi" query component "helper"
		 */
		@SafeVarargs
		@NonNull
		final public MultiQueryComponent<T> orderBy(final @NonNull Tuple2<String, Integer>... orderList) {
			if (null == _orderBy) {
				_orderBy = new ArrayList<Tuple2<String, Integer>>(Arrays.asList(orderList));
			}
			else {
				_orderBy.addAll(Arrays.asList(orderList));
			}
			return this;
		}		
		
		// Implementation
		
		protected Long _limit;
		protected List<Tuple2<String, Integer>> _orderBy;
		
		List<SingleQueryComponent<T>> _elements;
		Operator _op;
		
		protected MultiQueryComponent(final @NonNull Operator op, final @SuppressWarnings("unchecked") SingleQueryComponent<T>... components) {
			_op = op;
			_elements = new ArrayList<SingleQueryComponent<T>>(Arrays.asList(components)); 
		}
	}
	
	///////////////////////////////////////////////////////////////////
	
	/** Encapsulates a very simple query - this top level all
	 * @author acp
	 * @param <T> the bean type being queried
	 */
	public static class SingleQueryComponent<T> extends QueryComponent<T> {
		
		// Public interface - read
		// THIS IS FOR CRUD INTERFACE IMPLEMENTERS ONLY
		
		/* (non-Javadoc)
		 * @see com.ikanow.aleph2.data_model.utils.CrudUtils.QueryComponent#getOp()
		 */
		@NonNull
		public Operator getOp() {
			return _op;
		}

		/* (non-Javadoc)
		 * @see com.ikanow.aleph2.data_model.utils.CrudUtils.QueryComponent#getLimit()
		 */
		@NonNull
		public Long getLimit() {
			return _limit;
		}

		/* (non-Javadoc)
		 * @see com.ikanow.aleph2.data_model.utils.CrudUtils.QueryComponent#getOrderBy()
		 */
		@NonNull
		public List<Tuple2<String, Integer>> getOrderBy() {
			return _orderBy;
		}
		
		/** All query elements in this SingleQueryComponent 
		 * @return the list of all query elements in this SingleQueryComponent 
		 */
		@NonNull
		public LinkedHashMultimap<String, Tuple2<Operator, Tuple2<Object, Object>>> getAll() {
			// Take all the non-null fields from the raw object and add them as op_equals
			
			final LinkedHashMultimap<String, Tuple2<Operator, Tuple2<Object, Object>>> ret_val = LinkedHashMultimap.create();
			if (null != _extra) {
				ret_val.putAll(_extra);
			}			
			recursiveQueryBuilder_init(_element, true)
				.forEach(field_tuple -> ret_val.put(field_tuple._1(), Tuples._2T(Operator.equals, Tuples._2T(field_tuple._2(), null)))); 
			
			return ret_val;
		}
		
		/** The template spec used to generate an inital set
		 * @return The template spec used to generate an inital set
		 */
		@NonNull
		protected Object getElement() {
			return _element;
		}

		/** Elements added on top of the template spec
		 * @return a list of elements (not including those added via the build)
		 */
		@NonNull
		protected LinkedHashMultimap<String, Tuple2<Operator, Tuple2<Object, Object>>> getExtra() {
			return _extra;
		}

		// Public interface - build
		
		/** Converts this component to one that can be passed into a raw JsonNode version
		 * @return the Json equivalent query component
		 */
		@NonNull
		public SingleJsonQueryComponent<T> toJsonComponent() {
			return new SingleJsonQueryComponent<T>(this);
		}
		
		/** Adds a collection field to the query - any of which can match
		 * @param getter - the field name (dot notation supported)
		 * @param in - the collection of objects, any of which can match
		 * @return the Query Component helper
		 */
		@NonNull
		public SingleQueryComponent<T> withAny(final @NonNull String field, final @NonNull Collection<?> in) {
			return with(Operator.any_of, field, Tuples._2T(in, null));
		}
		/** Adds a collection field to the query - all of which must match
		 * @param getter - the field name (dot notation supported)
		 * @param in - the collection of objects, all of which must match
		 * @return the Query Component helper
		 */
		@NonNull
		public SingleQueryComponent<T> withAll(final @NonNull String field, final @NonNull Collection<?> in) {
			return with(Operator.all_of, field, Tuples._2T(in, null));
		}
		
		/** Adds the requirement that the field be greater (or equal, if exclusive is false) than the specified lower bound
		 * @param getter - the field name (dot notation supported)
		 * @param lower_bound - the lower bound - likely needs to be comparable, but not required by the API since that is up to the DB
		 * @param exclusive - if true, then the bound is _not_ included, if true then it is 
		 * @return the Query Component helper
		 */
		@NonNull
		public <U> SingleQueryComponent<T> rangeAbove(final @NonNull String field, final @NonNull U lower_bound, final boolean exclusive) {			
			return rangeIn(field, lower_bound, exclusive, null, false);
		}
		/** Adds the requirement that the field be lesser (or equal, if exclusive is false) than the specified lower bound
		 * @param getter - the field name (dot notation supported)
		 * @param upper_bound - the upper bound - likely needs to be comparable, but not required by the API since that is up to the DB
		 * @param exclusive - if true, then the bound is _not_ included, if true then it is 
		 * @return the Query Component helper
		 */
		@NonNull
		public <U> SingleQueryComponent<T> rangeBelow(final @NonNull String field, final @NonNull U upper_bound, final boolean exclusive) {
			return rangeIn(field, null, false, upper_bound, exclusive);
		}
		/** Adds the requirement that the field be within the two bounds (with exclusive/inclusive ie lower bound not included/included set by the 
		 * @param getter - the field name (dot notation supported)
		 * @param lower_bound - the lower bound - likely needs to be comparable, but not required by the API since that is up to the DB
		 * @param lower_exclusive - if true, then the bound is _not_ included, if true then it is
		 * @param upper_bound - the upper bound - likely needs to be comparable, but not required by the API since that is up to the DB
		 * @param upper_exclusive - if true, then the bound is _not_ included, if true then it is
		 * @return
		 */
		@NonNull
		public <U> SingleQueryComponent<T> rangeIn(final @NonNull String field, final @NonNull U lower_bound, final boolean lower_exclusive, final @NonNull U upper_bound, final boolean upper_exclusive) {
			java.util.function.BiFunction<Boolean, Boolean, Operator> getRange =
				(lower, upper) -> {
					if (lower && upper) {
						return Operator.range_open_open;
					}
					else if (upper) {
						return Operator.range_closed_open;						
					}
					else if (lower) {
						return Operator.range_open_closed;						
					}
					else {
						return Operator.range_closed_closed;												
					}
				};
			return with(getRange.apply(lower_exclusive, upper_exclusive), field, Tuples._2T(lower_bound, upper_bound));
		}		
		
		/** Adds the requirement that a field be present 
		 * @param field - the field name (dot notation supported)
		 * @return
		 */
		@NonNull
		public SingleQueryComponent<T> withPresent(final @NonNull String field) {
			return with(Operator.exists, field, Tuples._2T(true, null));
		}
		/** Adds the requirement that a field must not be present 
		 * @param field - the field name (dot notation supported)
		 * @return
		 */
		@NonNull
		public SingleQueryComponent<T> withNotPresent(final @NonNull String field) {
			return with(Operator.exists, field, Tuples._2T(false, null));
		}
		/** Adds the requirement that a field not be set to the given value 
		 * @param field - the field name (dot notation supported)
		 * @param value - the value to be negated
		 * @return the Query Component helper
		 */
		@NonNull
		public <U> SingleQueryComponent<T> whenNot(final @NonNull String field, final @NonNull U value) {
			return with(Operator.equals, field, Tuples._2T(null, value));
		}
		/** Adds the requirement that a field be set to the given value 
		 * @param field - the field name (dot notation supported)
		 * @param value - the value to be negated
		 * @return the Query Component helper
		 */
		@NonNull
		public <U> SingleQueryComponent<T> when(final @NonNull String field, final @NonNull U value) {
			return with(Operator.equals, field, Tuples._2T(value, null));
		}
		/** Enables nested queries to be represented
		 * @param field the parent field of the nested object
		 * @param nested_query_component
		 * @return the Query Component helper
		 */
		@NonNull
		public <U> SingleQueryComponent<T> nested(final @NonNull String field, final @NonNull SingleQueryComponent<U> nested_query_component) {
			
			// Take all the non-null fields from the raw object and add them as op_equals
			
			recursiveQueryBuilder_init(nested_query_component._element, true)
				.forEach(field_tuple -> this.with(Operator.equals, field + "." + field_tuple._1(), Tuples._2T(field_tuple._2(), null))); 
			
			// Easy bit, add the extras
			
			Optionals.ofNullable(nested_query_component._extra.entries()).stream()
				.forEach(entry -> this._extra.put(field + "." + entry.getKey(), entry.getValue()));
			
			return this;
		}
				
		/** Limits the number of returned objects (ignored if the query component is used in a multi-query)
		 * @param limit the max number of objects to retrieve
		 * @return the query component "helper"
		 */
		@NonNull
		public SingleQueryComponent<T> limit(final long limit) {
			_limit = limit;
			return this;
		}
		/** Specifies the order in which objects will be returned (ignored if the query component is used in a multi-query)
		 * @param orderList a list of 2-tupes, first is the field string, second is +1 for ascending, -1 for descending
		 * @return the "multi" query component "helper"
		 */
		@SafeVarargs
		@NonNull
		public final SingleQueryComponent<T> orderBy(final @NonNull Tuple2<String, Integer>... orderList) {
			if (null == _orderBy) {
				_orderBy = new ArrayList<Tuple2<String, Integer>>(Arrays.asList(orderList));
			}
			else {
				_orderBy.addAll(Arrays.asList(orderList));
			}
			return this;
		}
		
		// Implementation
		
		protected Object _element = null;
		protected Operator _op; 		
		LinkedHashMultimap<String, Tuple2<Operator, Tuple2<Object, Object>>> _extra = null;
		
		// Not supported when used in multi query
		protected Long _limit;
		protected List<Tuple2<String, Integer>> _orderBy;
		
		@NonNull
		protected SingleQueryComponent(final BeanTemplate<?> t, final @NonNull Operator op) {
			_element = t == null ? null : t.get();
			_op = op;
		}
		
		@NonNull
		protected SingleQueryComponent<T> with(final @NonNull Operator op, final @NonNull String field, Tuple2<Object, Object> in) {
			if (null == _extra) {
				_extra = LinkedHashMultimap.create();
			}
			_extra.put(field, Tuples._2T(op, in));
			return this;
		}		
	}
	
	///////////////////////////////////////////////////////////////////

	/** Helper class to generate queries for an object of type T represented by JSON
	 * @author acp
	 *
	 * @param <T> - the underlying type
	 */
	/**
	 * @author acp
	 *
	 * @param <T>
	 */
	@SuppressWarnings("unchecked")
	public static class SingleJsonQueryComponent<T> extends SingleQueryComponent<JsonNode> {
		
		// Public - build
		
		/** Enables nested queries to be represented
		 * NOTE: all getter variants must come before all string field variants
		 * @param getter - the Java8 getter for the parent field of the nested object
		 * @param nested_query_component
		 * @return the Query Component helper
		 */
		@NonNull
		public <U> SingleJsonQueryComponent<T> nested(final @NonNull Function<T, ?> getter, final @NonNull SingleQueryComponent<U> nested_query_component) {
			buildNamingHelper();
			return (SingleJsonQueryComponent<T>)nested(_naming_helper.field(getter), nested_query_component);
		}
		/** Adds a collection field to the query - any of which can match
		 * NOTE: all getter variants must come before all string field variants
		 * @param getter - the Java8 getter for the field
		 * @param in - the collection of objects, any of which can match
		 * @return the Query Component helper
		 */
		@NonNull
		public SingleJsonQueryComponent<T> withAny(final @NonNull Function<T, ?> getter, final @NonNull Collection<?> in) {
			buildNamingHelper();
			return (SingleJsonQueryComponent<T>)with(Operator.any_of, _naming_helper.field(getter), Tuples._2T(in, null));
		}
		/** Adds a collection field to the query - all of which must match
		 * NOTE: all getter variants must come before all string field variants
		 * @param getter the Java8 getter for the field
		 * @param in - the collection of objects, all of which must match
		 * @return the Query Component helper
		 */
		@NonNull
		public SingleJsonQueryComponent<T> withAll(final @NonNull Function<T, ?> getter, final @NonNull Collection<?> in) {
			buildNamingHelper();
			return (SingleJsonQueryComponent<T>)with(Operator.all_of, _naming_helper.field(getter), Tuples._2T(in, null));
		}
		/** Adds the requirement that the field be greater (or equal, if exclusive is false) than the specified lower bound
		 * NOTE: all getter variants must come before all string field variants
		 * @param getter the field name (dot notation supported)
		 * @param lower_bound - the lower bound - likely needs to be comparable, but not required by the API since that is up to the DB
		 * @param exclusive - if true, then the bound is _not_ included, if true then it is 
		 * @return the Query Component helper
		 */
		@NonNull
		public <U> SingleJsonQueryComponent<T> rangeAbove(final @NonNull Function<T, ?> getter, final @NonNull U lower_bound, final boolean exclusive) {			
			buildNamingHelper();
			return (SingleJsonQueryComponent<T>)rangeIn(_naming_helper.field(getter), lower_bound, exclusive, null, false);
		}
		/** Adds the requirement that the field be lesser (or equal, if exclusive is false) than the specified lower bound
		 * NOTE: all getter variants must come before all string field variants
		 * @param getter - the field name (dot notation supported)
		 * @param upper_bound - the upper bound - likely needs to be comparable, but not required by the API since that is up to the DB
		 * @param exclusive - if true, then the bound is _not_ included, if true then it is 
		 * @return the Query Component helper
		 */
		@NonNull
		public <U> SingleJsonQueryComponent<T> rangeBelow(final @NonNull Function<T, ?> getter, final @NonNull U upper_bound, final boolean exclusive) {
			buildNamingHelper();
			return (SingleJsonQueryComponent<T>)rangeIn(_naming_helper.field(getter), null, false, upper_bound, exclusive);
		}
		/** Adds the requirement that the field be within the two bounds (with exclusive/inclusive ie lower bound not included/included set by the 
		 * NOTE: all getter variants must come before all string field variants
		 * @param getter - the field name (dot notation supported)
		 * @param lower_bound - the lower bound - likely needs to be comparable, but not required by the API since that is up to the DB
		 * @param lower_exclusive - if true, then the bound is _not_ included, if true then it is
		 * @param upper_bound - the upper bound - likely needs to be comparable, but not required by the API since that is up to the DB
		 * @param upper_exclusive - if true, then the bound is _not_ included, if true then it is
		 * @return
		 */
		@NonNull
		public <U> SingleJsonQueryComponent<T> rangeIn(final @NonNull Function<T, ?> getter, final @NonNull U lower_bound, final boolean lower_exclusive, final @NonNull U upper_bound, final boolean upper_exclusive) {
			buildNamingHelper();
			return (SingleJsonQueryComponent<T>)rangeIn(_naming_helper.field(getter), lower_bound, lower_exclusive, upper_bound, upper_exclusive);
		}
		/** Adds the requirement that a field not be set to the given value 
		 * NOTE: all getter variants must come before all string field variants
		 * @param getter - the Java8 getter for the field
		 * @param value - the value to be negated
		 * @return the Query Component helper
		 */
		@NonNull
		public <U> SingleJsonQueryComponent<T> whenNot(final @NonNull Function<T, ?> getter, final @NonNull U value) {
			buildNamingHelper();
			return (SingleJsonQueryComponent<T>)with(Operator.equals, _naming_helper.field(getter), Tuples._2T(null, value));
		}
		/** Adds the requirement that a field be set to the given value 
		 * NOTE: all getter variants must come before all string field variants
		 * @param getter - the Java8 getter for the field
		 * @param value - the value to be negated
		 * @return the Query Component helper
		 */
		@NonNull
		public <U> SingleJsonQueryComponent<T> when(final @NonNull Function<T, ?> getter, final @NonNull U value) {
			buildNamingHelper();
			return (SingleJsonQueryComponent<T>)with(Operator.equals, _naming_helper.field(getter), Tuples._2T(value, null));
		}
		/** Adds the requirement that a field be present 
		 * NOTE: all getter variants must come before all string field variants
		 * @param getter - the Java8 getter for the field
		 * @return the Query Component helper
		 */
		@NonNull
		public SingleJsonQueryComponent<T> withPresent(final @NonNull Function<T, ?> getter) {
			buildNamingHelper();
			return (SingleJsonQueryComponent<T>)with(Operator.exists, _naming_helper.field(getter), Tuples._2T(true, null));
		}
		/** Adds the requirement that a field be missing 
		 * NOTE: all getter variants must come before all string field variants
		 * @param getter - the Java8 getter for the field
		 * @return the Query Component helper
		 */
		@NonNull
		public SingleJsonQueryComponent<T> withNotPresent(final @NonNull Function<T, ?> getter) {
			buildNamingHelper();
			return (SingleJsonQueryComponent<T>)with(Operator.exists, _naming_helper.field(getter), Tuples._2T(false, null));
		}
		
		// Implementation
		
		protected SingleJsonQueryComponent(final @NonNull BeanTemplate<T> t, final @NonNull Operator op) {
			super(t, op);
		}		
		protected void buildNamingHelper() {
			if (null == _naming_helper) {
				_naming_helper = BeanTemplateUtils.from((Class<T>) _element.getClass());
			}
		}
		
		protected MethodNamingHelper<T> _naming_helper = null;
		
		protected SingleJsonQueryComponent(final @NonNull SingleQueryComponent<T> copy) {
			super(BeanTemplate.of(copy._element), copy._op);
			
			_element = copy._element;
			_op = copy._op;
			_extra = copy._extra;
			
			// Not supported when used in multi query
			_limit = copy._limit;
			_orderBy = copy._orderBy;			
		}
	}
	
	///////////////////////////////////////////////////////////////////
	
	/** Helper class to generate queries for an object of type T
	 * @author acp
	 *
	 * @param <T> - the underlying type
	 */
	public static class SingleBeanQueryComponent<T> extends SingleQueryComponent<T> {
		// Public - build
		
		/** Enables nested queries to be represented
		 * NOTE: all getter variants must come before all string field variants
		 * @param getter - the Java8 getter for the parent field of the nested object
		 * @param nested_query_component
		 * @return the Query Component helper
		 */
		@NonNull
		public <U> SingleBeanQueryComponent<T> nested(final @NonNull Function<T, ?> getter, final @NonNull SingleQueryComponent<U> nested_query_component) {
			buildNamingHelper();
			return (SingleBeanQueryComponent<T>)nested(_naming_helper.field(getter), nested_query_component);
		}
		/** Adds a collection field to the query - any of which can match
		 * NOTE: all getter variants must come before all string field variants
		 * @param getter - the Java8 getter for the field
		 * @param in - the collection of objects, any of which can match
		 * @return the Query Component helper
		 */
		@NonNull
		public SingleBeanQueryComponent<T> withAny(final @NonNull Function<T, ?> getter, final @NonNull Collection<?> in) {
			buildNamingHelper();
			return (SingleBeanQueryComponent<T>)with(Operator.any_of, _naming_helper.field(getter), Tuples._2T(in, null));
		}
		/** Adds a collection field to the query - all of which must match
		 * NOTE: all getter variants must come before all string field variants
		 * @param getterthe Java8 getter for the field
		 * @param in - the collection of objects, all of which must match
		 * @return the Query Component helper
		 */
		@NonNull
		public SingleBeanQueryComponent<T> withAll(final @NonNull Function<T, ?> getter, final @NonNull Collection<?> in) {
			buildNamingHelper();
			return (SingleBeanQueryComponent<T>)with(Operator.all_of, _naming_helper.field(getter), Tuples._2T(in, null));
		}
		/** Adds the requirement that the field be greater (or equal, if exclusive is false) than the specified lower bound
		 * NOTE: all getter variants must come before all string field variants
		 * @param getter the field name (dot notation supported)
		 * @param lower_bound - the lower bound - likely needs to be comparable, but not required by the API since that is up to the DB
		 * @param exclusive - if true, then the bound is _not_ included, if true then it is 
		 * @return the Query Component helper
		 */
		@NonNull
		public <U> SingleBeanQueryComponent<T> rangeAbove(final @NonNull Function<T, ?> getter, final @NonNull U lower_bound, final boolean exclusive) {			
			buildNamingHelper();
			return (SingleBeanQueryComponent<T>)rangeIn(_naming_helper.field(getter), lower_bound, exclusive, null, false);
		}
		/** Adds the requirement that the field be lesser (or equal, if exclusive is false) than the specified lower bound
		 * NOTE: all getter variants must come before all string field variants
		 * @param getter - the field name (dot notation supported)
		 * @param upper_bound - the upper bound - likely needs to be comparable, but not required by the API since that is up to the DB
		 * @param exclusive - if true, then the bound is _not_ included, if true then it is 
		 * @return the Query Component helper
		 */
		@NonNull
		public <U> SingleBeanQueryComponent<T> rangeBelow(final @NonNull Function<T, ?> getter, final @NonNull U upper_bound, final boolean exclusive) {
			buildNamingHelper();
			return (SingleBeanQueryComponent<T>)rangeIn(_naming_helper.field(getter), null, false, upper_bound, exclusive);
		}
		/** Adds the requirement that the field be within the two bounds (with exclusive/inclusive ie lower bound not included/included set by the 
		 * NOTE: all getter variants must come before all string field variants
		 * @param getter - the field name (dot notation supported)
		 * @param lower_bound - the lower bound - likely needs to be comparable, but not required by the API since that is up to the DB
		 * @param lower_exclusive - if true, then the bound is _not_ included, if true then it is
		 * @param upper_bound - the upper bound - likely needs to be comparable, but not required by the API since that is up to the DB
		 * @param upper_exclusive - if true, then the bound is _not_ included, if true then it is
		 * @return
		 */
		@NonNull
		public <U> SingleBeanQueryComponent<T> rangeIn(final @NonNull Function<T, ?> getter, final @NonNull U lower_bound, final boolean lower_exclusive, U upper_bound, boolean upper_exclusive) {
			buildNamingHelper();
			return (SingleBeanQueryComponent<T>)rangeIn(_naming_helper.field(getter), lower_bound, lower_exclusive, upper_bound, upper_exclusive);
		}
		/** Adds the requirement that a field not be set to the given value 
		 * NOTE: all getter variants must come before all string field variants
		 * @param getter - the Java8 getter for the field
		 * @param value - the value to be negated
		 * @return the Query Component helper
		 */
		@NonNull
		public <U> SingleBeanQueryComponent<T> whenNot(final @NonNull Function<T, ?> getter, final @NonNull U value) {
			buildNamingHelper();
			return (SingleBeanQueryComponent<T>)with(Operator.equals, _naming_helper.field(getter), Tuples._2T(null, value));
		}
		/** Adds the requirement that a field be set to the given value 
		 * NOTE: all getter variants must come before all string field variants
		 * @param getter - the Java8 getter for the field
		 * @param value - the value to be negated
		 * @return the Query Component helper
		 */
		@NonNull
		public <U> SingleBeanQueryComponent<T> when(final @NonNull Function<T, ?> getter, final @NonNull U value) {
			buildNamingHelper();
			return (SingleBeanQueryComponent<T>)with(Operator.equals, _naming_helper.field(getter), Tuples._2T(value, null));
		}
		/** Adds the requirement that a field be present 
		 * NOTE: all getter variants must come before all string field variants
		 * @param getter - the Java8 getter for the field
		 * @return the Query Component helper
		 */
		@NonNull
		public SingleBeanQueryComponent<T> withPresent(final @NonNull Function<T, ?> getter) {
			buildNamingHelper();
			return (SingleBeanQueryComponent<T>)with(Operator.exists, _naming_helper.field(getter), Tuples._2T(true, null));
		}
		/** Adds the requirement that a field be missing 
		 * NOTE: all getter variants must come before all string field variants
		 * @param getter - the Java8 getter for the field
		 * @return the Query Component helper
		 */
		@NonNull
		public SingleBeanQueryComponent<T> withNotPresent(final @NonNull Function<T, ?> getter) {
			buildNamingHelper();
			return (SingleBeanQueryComponent<T>)with(Operator.exists, _naming_helper.field(getter), Tuples._2T(false, null));
		}
		
		// Implementation
		
		protected SingleBeanQueryComponent(final @NonNull BeanTemplate<T> t, final Operator op) {
			super(t, op);
		}
		@SuppressWarnings("unchecked")
		protected void buildNamingHelper() {
			if (null == _naming_helper) {
				_naming_helper = BeanTemplateUtils.from((Class<T>) _element.getClass());
			}
		}
		
		protected MethodNamingHelper<T> _naming_helper = null;
	}

	///////////////////////////////////////////////////////////////////	
	
	/** A component for a bean repo (rather than a raw JSON one)
	 * @author acp
	 *
	 * @param <T> - the bean type in the repo
	 */
	public static class BeanUpdateComponent<T> extends CommonUpdateComponent<T> {
		
		// Builders
		
		/** Increments the field
		 * @param field 
		 * @param n - the number to add to the field's current value
		 * @return the update component builder
		 */
		@NonNull
		public BeanUpdateComponent<T> increment(final @NonNull Function<T, ?> getter, final @NonNull Number n) {
			buildNamingHelper();
			return (BeanUpdateComponent<T>)with(UpdateOperator.increment, _naming_helper.field(getter), n);
		}
		
		/** Sets the field
		 * @param getter - the getter for this field
		 * @param o
		 * @return the update component builder
		 */
		@NonNull
		public BeanUpdateComponent<T> set(final @NonNull Function<T, ?> getter, final @NonNull Object o) {
			buildNamingHelper();
			return (BeanUpdateComponent<T>)with(UpdateOperator.set, _naming_helper.field(getter), o);
		}
		
		/** Unsets the field
		 * @param getter - the getter for this field
		 * @return the update component builder
		 */
		@NonNull
		public BeanUpdateComponent<T> unset(final @NonNull Function<T, ?> getter) {
			buildNamingHelper();
			return (BeanUpdateComponent<T>)with(UpdateOperator.unset, _naming_helper.field(getter), true);
		}
		
		/** Pushes the field into the array with the field
		 * @param getter - the getter for this field
		 * @param o - if o is a collection then each element is added
		 * @param dedup - if true then the object is not added if it already exists
		 * @return the update component builder
		 */
		@NonNull
		public BeanUpdateComponent<T> add(final @NonNull Function<T, ?> getter, final @NonNull Object o, final boolean dedup) {			
			buildNamingHelper();
			return (BeanUpdateComponent<T>)(dedup 
					? with(UpdateOperator.add_deduplicate, _naming_helper.field(getter), o) 
					: with(UpdateOperator.add, _naming_helper.field(getter), o));
		}
		
		/** Removes the object or objects from the field
		 * @param getter - the getter for this field
		 * @param o - if o is a collection then each element is removed
		 * @return the update component builder
		 */
		@NonNull
		public BeanUpdateComponent<T> remove(final @NonNull Function<T, ?> getter, final @NonNull Object o) {
			buildNamingHelper();
			return (BeanUpdateComponent<T>)with(UpdateOperator.remove, _naming_helper.field(getter), o);
		}
		
		/** Nests an update
		 * @param getter - the getter for this field
		 * @param nested_object - the update component to nest against parent field
		 * @return the update component builder
		 */
		@NonNull
		public <U> BeanUpdateComponent<T> nested(final @NonNull Function<T, ?> getter, final @NonNull CommonUpdateComponent<U> nested_object) {
			buildNamingHelper();
			return (BeanUpdateComponent<T>)nested(_naming_helper.field(getter), nested_object);
		}
		
		// Implementation
		
		@SuppressWarnings("unchecked")
		protected BeanUpdateComponent(final @NonNull BeanTemplate<T> bean_template) {
			super((BeanTemplate<Object>)bean_template);
		}
		@SuppressWarnings("unchecked")
		protected void buildNamingHelper() {
			if (null == _naming_helper) {
				_naming_helper = BeanTemplateUtils.from((Class<T>) _element.getClass());
			}
		}
		protected MethodNamingHelper<T> _naming_helper = null;
	}

	/** A component for a raw JSON repo (rather than a bean one)
	 * @author acp
	 *
	 * @param <T> - the bean type in the repo
	 */
	public static class JsonUpdateComponent<T> extends CommonUpdateComponent<JsonNode> {
		
		// Builders
		
		/** Increments the field
		 * @param field 
		 * @param n - the number to add to the field's current value
		 * @return the update component builder
		 */
		@SuppressWarnings("unchecked")
		@NonNull
		public JsonUpdateComponent<T> increment(final @NonNull Function<T, ?> getter, final @NonNull Number n) {
			buildNamingHelper();
			return (JsonUpdateComponent<T>)with(UpdateOperator.increment, _naming_helper.field(getter), n);
		}
		
		/** Sets the field
		 * @param getter - the getter for this field
		 * @param o
		 * @return the update component builder
		 */
		@SuppressWarnings("unchecked")
		@NonNull
		public JsonUpdateComponent<T> set(final @NonNull Function<T, ?> getter, final @NonNull Object o) {
			buildNamingHelper();
			return (JsonUpdateComponent<T>)with(UpdateOperator.set, _naming_helper.field(getter), o);
		}
		
		/** Unsets the field
		 * @param getter - the getter for this field
		 * @return the update component builder
		 */
		@SuppressWarnings("unchecked")
		@NonNull
		public JsonUpdateComponent<T> unset(final @NonNull Function<T, ?> getter) {
			buildNamingHelper();
			return (JsonUpdateComponent<T>)with(UpdateOperator.unset, _naming_helper.field(getter), true);
		}
		
		/** Pushes the field into the array with the field
		 * @param getter - the getter for this field
		 * @param o - if o is a collection then each element is added
		 * @param dedup - if true then the object is not added if it already exists
		 * @return the update component builder
		 */
		@SuppressWarnings("unchecked")
		@NonNull
		public JsonUpdateComponent<T> add(final @NonNull Function<T, ?> getter, final @NonNull Object o, final boolean dedup) {			
			buildNamingHelper();
			return (JsonUpdateComponent<T>)(dedup 
					? with(UpdateOperator.add_deduplicate, _naming_helper.field(getter), o) 
					: with(UpdateOperator.add, _naming_helper.field(getter), o));
		}
		
		/** Removes the object or objects from the field
		 * @param getter - the getter for this field
		 * @param o - if o is a collection then each element is removed
		 * @return the update component builder
		 */
		@SuppressWarnings("unchecked")
		@NonNull
		public JsonUpdateComponent<T> remove(final @NonNull Function<T, ?> getter, final @NonNull Object o) {
			buildNamingHelper();
			return (JsonUpdateComponent<T>)with(UpdateOperator.remove, _naming_helper.field(getter), o);
		}
		
		/** Nests an update
		 * @param getter - the getter for this field
		 * @param nested_object - the update component to nest against parent field
		 * @return the update component builder
		 */
		@SuppressWarnings("unchecked")
		@NonNull
		public <U> JsonUpdateComponent<T> nested(final @NonNull Function<T, ?> getter, final @NonNull CommonUpdateComponent<U> nested_object) {
			buildNamingHelper();
			return (JsonUpdateComponent<T>)nested(_naming_helper.field(getter), nested_object);
		}
		
		// Implementation
		
		protected JsonUpdateComponent(final @NonNull BeanTemplate<Object> t) {
			super(t);
		}		
		@SuppressWarnings("unchecked")
		protected void buildNamingHelper() {
			if (null == _naming_helper) {
				_naming_helper = BeanTemplateUtils.from((Class<T>) _element.getClass());
			}
		}
		protected MethodNamingHelper<T> _naming_helper = null;
	}

	/** Common building and getting functions
	 * @author acp
	 *
	 * @param <T> the type of the bean in the repository
	 */
	public static class CommonUpdateComponent<T> extends UpdateComponent<T> {

		// Builders
	
		/** Increments the field
		 * @param field 
		 * @param n - the number to add to the field's current value
		 * @return the update component builder
		 */
		@NonNull
		public CommonUpdateComponent<T> increment(final @NonNull String field, final @NonNull Number n) {
			return with(UpdateOperator.increment, field, n);
		}
		
		/** Sets the field
		 * @param field
		 * @param o
		 * @return the update component builder
		 */
		@NonNull
		public CommonUpdateComponent<T> set(final @NonNull String field, final @NonNull Object o) {
			return with(UpdateOperator.set, field, o);
		}
		
		/** Unsets the field
		 * @param field
		 * @return the update component builder
		 */
		@NonNull
		public CommonUpdateComponent<T> unset(final @NonNull String field) {
			return with(UpdateOperator.unset, field, true);
		}
		
		/** Pushes the field into the array with the field
		 * @param field
		 * @param o - if o is a collection then each element is added
		 * @param dedup - if true then the object is not added if it already exists
		 * @return the update component builder
		 */
		@NonNull
		public CommonUpdateComponent<T> add(final @NonNull String field, final @NonNull Object o, final boolean dedup) {			
			return dedup ? with(UpdateOperator.add_deduplicate, field, o) : with(UpdateOperator.add, field, o);
		}
		
		/** Removes the object or objects from the field
		 * @param field
		 * @param o - if o is a collection then each element is removed
		 * @return the update component builder
		 */
		@NonNull
		public CommonUpdateComponent<T> remove(final @NonNull String field, final @NonNull Object o) {
			return with(UpdateOperator.remove, field, o);
		}
		
		/** Indicates that the object should be deleted
		 * @return the update component builder
		 */
		@NonNull
		public CommonUpdateComponent<T> deleteObject() {
			return with(UpdateOperator.unset, "", null);
		}		
		
		/** Nests an update
		 * @param field
		 * @param nested_object - the update component to nest against parent field
		 * @return the update component builder
		 */
		@NonNull
		public <U> CommonUpdateComponent<T> nested(final @NonNull String field, final @NonNull CommonUpdateComponent<U> nested_object) {
			
			// Take all the non-null fields from the raw object and add them as op_equals
			
			recursiveQueryBuilder_init(nested_object._element, false)
				.forEach(field_tuple -> this.with(UpdateOperator.set, field + "." + field_tuple._1(), field_tuple._2())); 
			
			// Easy bit, add the extras
			
			Optionals.ofNullable(nested_object._extra.entries()).stream()
				.forEach(entry -> this._extra.put(field + "." + entry.getKey(), entry.getValue()));
			
			return this;
		}
		
		// Public interface - read
		// THIS IS FOR CRUD INTERFACE IMPLEMENTERS ONLY
		
		/* (non-Javadoc)
		 * @see com.ikanow.aleph2.data_model.utils.CrudUtils.UpdateComponent#getElementClass()
		 */
		@SuppressWarnings("unchecked")
		@NonNull
		@Override
		public Class<T> getElementClass() {
			return (@NonNull Class<T>) _element.getClass();
		}
		
		@NonNull
		protected CommonUpdateComponent<T> with(final @NonNull UpdateOperator op, final @NonNull String field, Object in) {
			if (null == _extra) {
				_extra = LinkedHashMultimap.create();
			}
			_extra.put(field, Tuples._2T(op, in));
			return this;
		}
		
		/** All query elements in this SingleQueryComponent 
		 * @return the list of all query elements in this SingleQueryComponent 
		 */
		@NonNull
		public LinkedHashMultimap<String, Tuple2<UpdateOperator, Object>> getAll() {
			// Take all the non-null fields from the raw object and add them as op_equals
			
			final LinkedHashMultimap<String, Tuple2<UpdateOperator, Object>> ret_val = LinkedHashMultimap.create();
			if (null != _extra) {
				ret_val.putAll(_extra);
			}		
			
			recursiveQueryBuilder_init(_element, false)
				.forEach(field_tuple -> ret_val.put(field_tuple._1(), Tuples._2T(UpdateOperator.set, field_tuple._2()))); 
			
			return ret_val;
		}
	
		// Implementation
		
		protected final Object _element;
		protected LinkedHashMultimap<String, Tuple2<UpdateOperator, Object>> _extra = null;
		
		protected CommonUpdateComponent(final @NonNull BeanTemplate<Object> t) {
			_element = t.get();
		}				
		//Recursive helper:
		
	}	
	@NonNull
	protected static Stream<Tuple2<String, Object>> recursiveQueryBuilder_init(final Object bean, boolean denest) {
		if (null == bean) { // (for handling JSON, where you don't have a bean)
			return Stream.of();
		}
		//TODO (ALEPH-22) sometimes you need to nest the functionality and sometimes you don't... (eg updates normally not? queries normally
		// but actually the behavior is different .. to test this desnest==false, need to bring mongojack into the test context
		// so can test it for MongoDB
		
		return 	Arrays.stream(bean.getClass().getDeclaredFields())
				.filter(f -> !Modifier.isStatic(f.getModifiers())) // (ignore static fields)
				.flatMap(field_accessor -> {
					try { 
						field_accessor.setAccessible(true);
						Object val = field_accessor.get(bean);
						
						return Patterns.match(val)
								.<Stream<Tuple2<String, Object>>>andReturn()
								.when(v -> null == v, v -> Stream.empty())
								.when(String.class, v -> Stream.of(Tuples._2T(field_accessor.getName(), v)))
								.when(Number.class, v -> Stream.of(Tuples._2T(field_accessor.getName(), v)))
								.when(Boolean.class, v -> Stream.of(Tuples._2T(field_accessor.getName(), v)))
								.when(Collection.class, l -> Stream.of(Tuples._2T(field_accessor.getName(), l))) // (note can't denest objects/bean templates, that will exception out)
								.when(Map.class, v -> Stream.of(Tuples._2T(field_accessor.getName(), v)))
								.when(Multimap.class, v -> Stream.of(Tuples._2T(field_accessor.getName(), v)))
								// OK if it's none of these supported types that we recognize, then assume it's a bean and recursively de-nest it
								.when(BeanTemplate.class, v -> denest, v -> recursiveQueryBuilder_recurse(field_accessor.getName(), v))
								.when(BeanTemplate.class, v -> Stream.of(Tuples._2T(field_accessor.getName(), v)))
								.when(v -> denest, v -> recursiveQueryBuilder_recurse(field_accessor.getName(), BeanTemplate.of(v)))
								.otherwise(v -> Stream.of(Tuples._2T(field_accessor.getName(), BeanTemplate.of(v))));
					} 
					catch (Exception e) { return null; }
				});
	}
	
	@NonNull
	protected static Stream<Tuple2<String, Object>> recursiveQueryBuilder_recurse(final @NonNull String parent_field, final @NonNull BeanTemplate<?> sub_bean) {
		final LinkedHashMultimap<String, @NonNull Tuple2<Operator, Tuple2<Object, Object>>> ret_val = CrudUtils.allOf(sub_bean).getAll();
			//(all vs and inherited from parent so ignored here)			
		
		return ret_val.entries().stream().map(e -> Tuples._2T(parent_field + "." + e.getKey(), e.getValue()._2()._1()));
	}

}
