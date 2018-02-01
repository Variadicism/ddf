package org.codice.ddf.catalog.ui.scheduling.subscribers;

import com.google.common.collect.ImmutableCollection;
import ddf.catalog.operation.QueryResponse;
import ddf.util.Fallible;
import java.util.Map;

/**
 * This interface represents a service designed to deliver query results to a destination outside of
 * DDF, e.g., an email address.
 *
 * <p><b> This code is experimental. While this interface is functional and tested, it may change or
 * be removed in a future version of the library. </b>
 *
 * @author connor
 */
public interface QueryDeliveryService {
  /**
   * The key used to identify the type of delivery in a JSON object describing a delivery method.
   * This can be used to identify a query delivery service to be used for a delivery method by
   * matching the value of this key to the value returned from {@link
   * QueryDeliveryService#getDeliveryType()}.
   */
  String DELIVERY_TYPE_KEY = "deliveryType";

  /**
   * The key used to identify the display name of a {@link QueryDeliveryService} in the information
   * given to the frontend describing a {@link QueryDeliveryService}.
   */
  String DISPLAY_NAME_KEY = "displayName";

  /**
   * Deliver the given query results to a destination described by the given parameters.
   *
   * @param queryMetacardData the data describing the contents of a query metacard with the query
   *     executed to obtain the given query results.
   * @param queryResults the query results to be sent to the designated destination.
   * @param userID the ID of the user effectively running this query.
   * @param deliveryID the ID identifying this delivery method in the given user's preferences.
   * @param parameters the parameters specific to the called {@link QueryDeliveryService} instance,
   *     e.g., an email address; the contents of this map are expected to have keys matching the
   *     names and values matching the types given by {@link
   *     QueryDeliveryService#getRequiredFields()}.
   * @return a {@link Fallible} to indicate status by the presence or absence of an error; any value
   *     contained within is not useful.
   */
  Fallible<?> deliver(
      Map<String, Object> queryMetacardData,
      QueryResponse queryResults,
      String userID,
      String deliveryID,
      Map<String, Object> parameters);

  /**
   * @return a string describing the type of delivery methods that this {@link QueryDeliveryService}
   *     supports. This string is expected to be unique among all available {@link
   *     QueryDeliveryService}s.
   */
  String getDeliveryType();

  /**
   * @return a human-readable string naming this {@link QueryDeliveryService} intended to be used
   *     for UI display purposes. This string is not guaranteed to be unique in any way.
   */
  String getDisplayName();

  /**
   * @return a {@link java.util.Collection Collection} of {@link QueryDeliveryParameter}s describing
   *     all parameters required by this {@link QueryDeliveryService} to successfully complete its
   *     deliveries. The {@link QueryDeliveryParameter#getName() name} indicates a key expected to
   *     be present in parameters passed to {@link QueryDeliveryService#deliver(Map, QueryResponse,
   *     String, String, Map) QueryDeliveryService.deliver}; the {@link
   *     QueryDeliveryParameter#getType() type} describes the type of value expected to be
   *     associated with the related key in the same parameters.
   */
  ImmutableCollection<QueryDeliveryParameter> getRequiredFields();
}
