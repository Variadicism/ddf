/**
 * Copyright (c) Codice Foundation
 *
 * <p>
 *
 * <p>This is free software: you can redistribute it and/or modify it under the terms of the GNU
 * Lesser General Public License as published by the Free Software Foundation, either version 3 of
 * the License, or any later version.
 *
 * <p>
 *
 * <p>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details. A copy of the GNU Lesser General Public
 * License is distributed along with this program and can be found at
 * <http://www.gnu.org/licenses/lgpl.html>.
 */
package org.codice.ddf.catalog.ui.scheduling;

import static ddf.util.Fallible.error;
import static ddf.util.Fallible.forEach;
import static ddf.util.Fallible.of;
import static ddf.util.Fallible.success;

import com.google.common.annotations.VisibleForTesting;
import ddf.catalog.CatalogFramework;
import ddf.catalog.Constants;
import ddf.catalog.data.Metacard;
import ddf.catalog.federation.FederationException;
import ddf.catalog.operation.CreateResponse;
import ddf.catalog.operation.DeleteResponse;
import ddf.catalog.operation.Query;
import ddf.catalog.operation.QueryRequest;
import ddf.catalog.operation.QueryResponse;
import ddf.catalog.operation.Response;
import ddf.catalog.operation.Update;
import ddf.catalog.operation.UpdateResponse;
import ddf.catalog.operation.impl.QueryImpl;
import ddf.catalog.operation.impl.QueryRequestImpl;
import ddf.catalog.plugin.PluginExecutionException;
import ddf.catalog.plugin.PostIngestPlugin;
import ddf.catalog.source.SourceUnavailableException;
import ddf.catalog.source.UnsupportedQueryException;
import ddf.util.Fallible;
import ddf.util.MapUtils;
import java.nio.charset.Charset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.IgniteException;
import org.apache.ignite.IgniteScheduler;
import org.apache.ignite.IgniteState;
import org.apache.ignite.Ignition;
import org.apache.ignite.scheduler.SchedulerFuture;
import org.apache.ignite.transactions.TransactionException;
import org.boon.json.JsonException;
import org.boon.json.JsonFactory;
import org.codice.ddf.catalog.ui.metacard.workspace.QueryMetacardTypeImpl;
import org.codice.ddf.catalog.ui.metacard.workspace.WorkspaceAttributes;
import org.codice.ddf.catalog.ui.metacard.workspace.WorkspaceMetacardImpl;
import org.codice.ddf.catalog.ui.metacard.workspace.WorkspaceTransformer;
import org.codice.ddf.catalog.ui.scheduling.subscribers.QueryDeliveryService;
import org.codice.ddf.persistence.PersistenceException;
import org.codice.ddf.persistence.PersistentStore;
import org.codice.ddf.persistence.PersistentStore.PersistenceType;
import org.codice.ddf.security.common.Security;
import org.geotools.filter.text.cql2.CQLException;
import org.geotools.filter.text.ecql.ECQL;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.opengis.filter.Filter;
import org.opengis.filter.sort.SortBy;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.InvalidSyntaxException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class QuerySchedulingPostIngestPlugin implements PostIngestPlugin {
  private static final Logger LOGGER =
      LoggerFactory.getLogger(QuerySchedulingPostIngestPlugin.class);

  public static final String DELIVERY_METHODS_KEY = "deliveryMethods";

  public static final String DELIVERY_METHOD_ID_KEY = "deliveryId";

  public static final String DELIVERY_OPTIONS_KEY = "deliveryOptions";

  public static final String QUERIES_CACHE_NAME = "scheduled queries";

  public static final long QUERY_TIMEOUT_MS = TimeUnit.SECONDS.toMillis(10);

  private static final DateTimeFormatter ISO_8601_DATE_FORMAT =
      DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ").withZoneUTC();

  private static final Security SECURITY = Security.getInstance();

  private final BundleContext bundleContext =
      FrameworkUtil.getBundle(QuerySchedulingPostIngestPlugin.class).getBundleContext();

  /**
   * This {@link IgniteScheduler} can be used to schedule jobs to run according to a cron
   * expression.
   *
   * <p>This {@link IgniteCache} will become available as soon as a job is scheduled if a running
   * {@link Ignite} instance is available.
   */
  private static Fallible<IgniteScheduler> scheduler =
      error(
          "An Ignite scheduler has not been obtained for this query! Have any queries been started yet?");

  /**
   * This {@link IgniteCache} relates metacards to running {@link Ignite} scheduled jobs. Keys are
   * the metacard IDs of query metacards with scheduled jobs running; the values are unused.
   *
   * <p>This {@link IgniteCache} will become available as soon as a job is scheduled if a running
   * {@link Ignite} instance is available.
   */
  @VisibleForTesting
  static Fallible<IgniteCache<String, Integer>> runningQueries =
      error(
          "An Ignite cache has not been obtained for this query! Have any queries been started yet?");

  private final CatalogFramework catalogFramework;

  private final PersistentStore persistentStore;

  private final WorkspaceTransformer workspaceTransformer;

  public QuerySchedulingPostIngestPlugin(
      CatalogFramework catalogFramework,
      PersistentStore persistentStore,
      WorkspaceTransformer workspaceTransformer) {
    this.catalogFramework = catalogFramework;
    this.persistentStore = persistentStore;
    this.workspaceTransformer = workspaceTransformer;

    // TODO TEMP
    LOGGER.warn("Query scheduling plugin created!");
  }

  private Fallible<QueryResponse> runQuery(final String cqlQuery) {
    // TODO TEMP
    LOGGER.warn("Emailing metacard owner...");

    Filter filter;
    try {
      filter = ECQL.toFilter(cqlQuery);
    } catch (CQLException exception) {
      return error(
          "There was a problem reading the given query expression: " + exception.getMessage());
    }

    final Query query =
        new QueryImpl(
            filter, 1, Constants.DEFAULT_PAGE_SIZE, SortBy.NATURAL_ORDER, true, QUERY_TIMEOUT_MS);
    final QueryRequest queryRequest = new QueryRequestImpl(query, true);

    return SECURITY
        .runAsAdmin(SECURITY::getSystemSubject)
        .execute(
            () -> {
              try {
                return of(catalogFramework.query(queryRequest));
              } catch (UnsupportedQueryException exception) {
                return error(
                    "The query \"%s\" is not supported by the given catalog framework: %s",
                    cqlQuery, exception.getMessage());
              } catch (SourceUnavailableException exception) {
                return error(
                    "The catalog framework sources were unavailable: %s", exception.getMessage());
              } catch (FederationException exception) {
                return error(
                    "There was a problem with executing a federated search for the query \"%s\": %s",
                    cqlQuery, exception.getMessage());
              }
            });
  }

  private Fallible<?> deliver(
      final String deliveryType,
      final Map<String, Object> queryMetacardData,
      final QueryResponse results,
      final String userID,
      final String deliveryID,
      final Map<String, Object> deliveryParameters) {
    final String filter = String.format("(objectClass=%s)", QueryDeliveryService.class.getName());

    final Stream<QueryDeliveryService> deliveryServices;
    try {
      deliveryServices =
          bundleContext
              .getServiceReferences(QueryDeliveryService.class, filter)
              .stream()
              .map(bundleContext::getService)
              .filter(Objects::nonNull);
    } catch (InvalidSyntaxException exception) {
      return error(
          "The filter used to search for query delivery services, \"%s\", was invalid: %s",
          filter, exception.getMessage());
    }

    final List<QueryDeliveryService> selectedServices =
        deliveryServices
            .filter(deliveryService -> deliveryService.getDeliveryType().equals(deliveryType))
            .collect(Collectors.toList());

    if (selectedServices.isEmpty()) {
      return error(
          "The delivery method \"%s\" was not recognized; this query scheduling system found the following delivery methods: %s.",
          deliveryType,
          deliveryServices.map(QueryDeliveryService::getDeliveryType).collect(Collectors.toList()));
    } else if (selectedServices.size() > 1) {
      final String selectedServicesString =
          selectedServices
              .stream()
              .map(selectedService -> selectedService.getClass().getCanonicalName())
              .collect(Collectors.joining(", "));
      return error(
          "%d delivery services were found to handle the delivery type %s: %s.",
          selectedServices.size(), deliveryType, selectedServicesString);
    }

    return selectedServices
        .get(0)
        .deliver(queryMetacardData, results, userID, deliveryID, deliveryParameters);
  }

  private Fallible<Map<String, Object>> getUserPreferences(final String userID) {
    List<Map<String, Object>> preferencesList;
    try {
      preferencesList =
          persistentStore.get(
              PersistenceType.PREFERENCES_TYPE.toString(), String.format("user = '%s'", userID));
    } catch (PersistenceException exception) {
      return error(
          "There was a problem attempting to retrieve the preferences for user '%s': %s",
          userID, exception.getMessage());
    }
    if (preferencesList.size() != 1) {
      return error(
          "There were %d preference entries found for user '%s'!", preferencesList.size(), userID);
    }
    final Map<String, Object> preferencesItem = preferencesList.get(0);

    return MapUtils.tryGet(preferencesItem, "preferences_json_bin", byte[].class)
        .tryMap(
            json -> {
              final Map<String, Object> preferences;
              try {
                preferences =
                    JsonFactory.create()
                        .parser()
                        .parseMap(new String(json, Charset.defaultCharset()));
              } catch (JsonException exception) {
                return error(
                    "There was an error parsing the preferences for user '%s': %s",
                    userID, exception.getMessage());
              }

              return of(preferences);
            });
  }

  private Fallible<Pair<String, Map<String, Object>>> getDeliveryInfo(
      final Map<String, Object> userPreferences, final String deliveryID) {
    return MapUtils.tryGet(userPreferences, DELIVERY_METHODS_KEY, List.class)
        .tryMap(
            userDeliveryMethods -> {
              final List<Map<String, Object>> matchingDestinations =
                  ((List<Map<String, Object>>) userDeliveryMethods)
                      .stream()
                      .filter(
                          destination ->
                              MapUtils.tryGet(destination, DELIVERY_METHOD_ID_KEY, String.class)
                                  .map(deliveryID::equals)
                                  .orDo(
                                      error -> {
                                        LOGGER.error(
                                            "There was a problem attempting to retrieve the ID for a destination in the given preferences: %s",
                                            error);
                                        return false;
                                      }))
                      .collect(Collectors.toList());
              if (matchingDestinations.size() != 1) {
                return error(
                    "There were %d destinations matching the ID \"%s\" in the given preferences; only one is expected!",
                    matchingDestinations.size(), deliveryID);
              }
              final Map<String, Object> destinationData = matchingDestinations.get(0);

              return MapUtils.tryGetAndRun(
                  destinationData,
                  QueryDeliveryService.DELIVERY_TYPE_KEY,
                  String.class,
                  DELIVERY_OPTIONS_KEY,
                  Map.class,
                  (deliveryType, deliveryOptions) ->
                      of(ImmutablePair.of(deliveryType, (Map<String, Object>) deliveryOptions)));
            });
  }

  private Fallible<?> deliverAll(
      final Collection<String> scheduleDeliveryIDs,
      final String scheduleUserID,
      final Map<String, Object> queryMetacardData,
      final QueryResponse results) {
    return getUserPreferences(scheduleUserID)
        .tryMap(
            userPreferences ->
                forEach(
                    scheduleDeliveryIDs,
                    deliveryID ->
                        getDeliveryInfo(userPreferences, deliveryID)
                            .prependToError(
                                "There was a problem retrieving the delivery information with ID \"%s\" for user '%s': ",
                                deliveryID, scheduleUserID)
                            .tryMap(
                                deliveryInfo ->
                                    deliver(
                                            deliveryInfo.getLeft(),
                                            queryMetacardData,
                                            results,
                                            scheduleUserID,
                                            deliveryID,
                                            deliveryInfo.getRight())
                                        .prependToError(
                                            "There was a problem delivering query results to delivery info with ID \"%s\" for user '%s': ",
                                            deliveryID, scheduleUserID))));
  }

  private Fallible<SchedulerFuture<?>> scheduleJob(
      final IgniteScheduler scheduler,
      final Map<String, Object> queryMetacardData,
      final String queryMetacardID,
      final String cqlQuery,
      final String scheduleUserID,
      final int scheduleInterval,
      final String scheduleUnit,
      final String scheduleStartString,
      final String scheduleEndString,
      final List<String> scheduleDeliveryIDs) {
    if (scheduleInterval <= 0) {
      return error("A task cannot be executed every %d %s!", scheduleInterval, scheduleUnit);
    }

    DateTime start;
    DateTime end;
    try {
      start = DateTime.parse(scheduleStartString, ISO_8601_DATE_FORMAT);
    } catch (DateTimeParseException exception) {
      return error(
          "The start date attribute of this metacard, \"%s\", could not be parsed: %s",
          scheduleStartString, exception.getMessage());
    }
    try {
      end = DateTime.parse(scheduleEndString, ISO_8601_DATE_FORMAT);
    } catch (DateTimeParseException exception) {
      return error(
          "The end date attribute of this metacard, \"%s\", could not be parsed: %s",
          scheduleStartString, exception.getMessage());
    }

    RepetitionTimeUnit unit;
    try {
      unit = RepetitionTimeUnit.valueOf(scheduleUnit.toUpperCase());
    } catch (IllegalArgumentException exception) {
      return error(
          "The unit of time \"%s\" for the scheduled query time interval is not recognized!",
          scheduleUnit);
    }

    final SchedulerFuture<?> job;
    try {
      job =
          scheduler.scheduleLocal(
              new Runnable() {
                // Set this >= scheduleInterval - 1 so that a scheduled query executes the first
                // time it is able
                private int unitsPassedSinceStarted = scheduleInterval;

                @Override
                public void run() {
                  final boolean isRunning =
                      runningQueries
                          .map(runningQueries -> runningQueries.containsKey(queryMetacardID))
                          .or(true);
                  if (!isRunning) {
                    return;
                  }

                  final DateTime now = DateTime.now();
                  if (start.compareTo(now) <= 0) {
                    if (unitsPassedSinceStarted < scheduleInterval - 1) {
                      unitsPassedSinceStarted++;
                      return;
                    }

                    unitsPassedSinceStarted = 0;

                    runQuery(cqlQuery)
                        .tryMap(
                            results ->
                                deliverAll(
                                    scheduleDeliveryIDs,
                                    scheduleUserID,
                                    queryMetacardData,
                                    results))
                        .elseDo(LOGGER::error);
                  }
                }
              },
              unit.makeCronToRunEachUnit(start));
    } catch (IgniteException exception) {
      return error(
          "There was a problem attempting to schedule a job for a query metacard \"%s\": %s",
          queryMetacardID, exception.getMessage());
    }

    runningQueries.ifValue(runningQueries -> runningQueries.put(queryMetacardID, 0));

    job.listen(
        future ->
            runningQueries.ifValue(
                runningQueries -> {
                  if (future instanceof SchedulerFuture) {
                    final SchedulerFuture<?> jobFuture = (SchedulerFuture<?>) future;
                    if (jobFuture.nextExecutionTime() == 0
                        || jobFuture.nextExecutionTime() > end.getMillis()
                        || !runningQueries.containsKey(queryMetacardID)) {
                      runningQueries.remove(queryMetacardID);
                      jobFuture.cancel();
                    }
                  }
                }));

    return of(job);
  }

  private Fallible<?> readScheduleDataAndSchedule(
      final IgniteScheduler scheduler,
      final Map<String, Object> queryMetacardData,
      final String queryMetacardId,
      final String cqlQuery,
      final Map<String, Object> scheduleData) {
    return MapUtils.tryGet(scheduleData, ScheduleMetacardTypeImpl.IS_SCHEDULED, Boolean.class)
        .tryMap(
            isScheduled -> {
              if (!isScheduled) {
                return success().mapValue(null);
              }

              return MapUtils.tryGetAndRun(
                  scheduleData,
                  ScheduleMetacardTypeImpl.SCHEDULE_USER_ID,
                  String.class,
                  ScheduleMetacardTypeImpl.SCHEDULE_AMOUNT,
                  Integer.class,
                  ScheduleMetacardTypeImpl.SCHEDULE_UNIT,
                  String.class,
                  ScheduleMetacardTypeImpl.SCHEDULE_START,
                  String.class,
                  ScheduleMetacardTypeImpl.SCHEDULE_END,
                  String.class,
                  ScheduleMetacardTypeImpl.SCHEDULE_DELIVERY_IDS,
                  List.class,
                  (scheduleUserID,
                      scheduleInterval,
                      scheduleUnit,
                      scheduleStartString,
                      scheduleEndString,
                      scheduleDeliveries) ->
                      scheduleJob(
                          scheduler,
                          queryMetacardData,
                          queryMetacardId,
                          cqlQuery,
                          scheduleUserID,
                          scheduleInterval,
                          scheduleUnit,
                          scheduleStartString,
                          scheduleEndString,
                          (List<String>) scheduleDeliveries));
            });
  }

  private Fallible<?> readQueryMetacardAndSchedule(final Map<String, Object> queryMetacardData) {
    if (Ignition.state() != IgniteState.STARTED) {
      return error("Cron queries cannot be scheduled without a running Ignite instance!");
    }

    final Ignite ignite = Ignition.ignite();

    final IgniteScheduler scheduler =
        QuerySchedulingPostIngestPlugin.scheduler.orDo(
            error -> {
              final IgniteScheduler newScheduler = ignite.scheduler();
              QuerySchedulingPostIngestPlugin.scheduler = of(newScheduler);
              return newScheduler;
            });

    final IgniteCache<String, ?> runningQueries =
        QuerySchedulingPostIngestPlugin.runningQueries.orDo(
            error -> {
              final IgniteCache<String, Integer> newCache =
                  ignite.getOrCreateCache(QUERIES_CACHE_NAME);
              QuerySchedulingPostIngestPlugin.runningQueries = of(newCache);
              return newCache;
            });

    return MapUtils.tryGetAndRun(
        queryMetacardData,
        Metacard.ID,
        String.class,
        queryMetacardId -> {
          if (runningQueries.containsKey(queryMetacardId)) {
            return error(
                "This query cannot be scheduled because a job is already scheduled for it!");
          }

          return MapUtils.tryGetAndRun(
              queryMetacardData,
              QueryMetacardTypeImpl.QUERY_CQL,
              String.class,
              QueryMetacardTypeImpl.QUERY_SCHEDULES,
              List.class,
              (cqlQuery, schedulesData) ->
                  forEach(
                      (List<Map<String, Object>>) schedulesData,
                      scheduleData ->
                          readScheduleDataAndSchedule(
                              scheduler,
                              queryMetacardData,
                              queryMetacardId,
                              cqlQuery,
                              scheduleData)));
        });
  }

  private static Fallible<?> cancelSchedule(final String queryMetacardId) {
    return runningQueries.tryMap(
        runningQueries -> {
          try {
            runningQueries.remove(queryMetacardId);
          } catch (TransactionException exception) {
            return error(
                "There was a problem attempting to cancel a job for the query metacard \"%s\": %s",
                queryMetacardId, exception.getMessage());
          }
          return success();
        });
  }

  private Fallible<?> readQueryMetacardAndCancelSchedule(
      final Map<String, Object> queryMetacardData) {
    return MapUtils.tryGet(queryMetacardData, Metacard.ID, String.class)
        .tryMap(QuerySchedulingPostIngestPlugin::cancelSchedule);
  }

  private Fallible<?> processMetacard(
      Metacard workspaceMetacard, Function<Map<String, Object>, Fallible<?>> metacardAction) {
    if (!WorkspaceMetacardImpl.isWorkspaceMetacard(workspaceMetacard)) {
      return success();
    }

    final Map<String, Object> workspaceMetacardData =
        workspaceTransformer.transform(workspaceMetacard);

    if (!workspaceMetacardData.containsKey(WorkspaceAttributes.WORKSPACE_QUERIES)) {
      return success();
    }

    return MapUtils.tryGet(workspaceMetacardData, WorkspaceAttributes.WORKSPACE_QUERIES, List.class)
        .tryMap(
            queryMetacardsData ->
                forEach(
                    (List<Map<String, Object>>) queryMetacardsData,
                    queryMetacardData -> {
                      if (!queryMetacardData.containsKey(QueryMetacardTypeImpl.QUERY_SCHEDULES)) {
                        return success();
                      }

                      return metacardAction.apply(queryMetacardData);
                    }));
  }

  private static void throwErrorsIfAny(List<ImmutablePair<Metacard, String>> errors)
      throws PluginExecutionException {
    if (!errors.isEmpty()) {
      throw new PluginExecutionException(
          errors
              .stream()
              .map(
                  metacardAndError ->
                      String.format(
                          "There was an error attempting to modify schedule execution of workspace metacard \"%s\": %s",
                          metacardAndError.getLeft().getId(), metacardAndError.getRight()))
              .collect(Collectors.joining("\n")));
    }
  }

  private <T extends Response> T processSingularResponse(
      T response,
      List<Metacard> metacards,
      Function<Map<String, Object>, Fallible<?>> metacardAction)
      throws PluginExecutionException {
    List<ImmutablePair<Metacard, String>> errors = new ArrayList<>();

    for (Metacard metacard : metacards) {
      // TODO TEMP
      LOGGER.debug(
          String.format("Processing metacard of type %s...", metacard.getMetacardType().getName()));
      processMetacard(metacard, metacardAction)
          .elseDo(error -> errors.add(ImmutablePair.of(metacard, error)));
    }

    throwErrorsIfAny(errors);

    return response;
  }

  @Override
  public CreateResponse process(CreateResponse creation) throws PluginExecutionException {
    // TODO TEMP
    LOGGER.warn("Processing creation...");
    return processSingularResponse(
        creation, creation.getCreatedMetacards(), this::readQueryMetacardAndSchedule);
  }

  @Override
  public UpdateResponse process(UpdateResponse updates) throws PluginExecutionException {
    // TODO TEMP
    LOGGER.warn("Processing update...");
    List<ImmutablePair<Metacard, String>> errors = new ArrayList<>();

    for (Update update : updates.getUpdatedMetacards()) {
      // TODO TEMP
      LOGGER.warn(
          String.format(
              "Processing old metacard of type %s...",
              update.getOldMetacard().getMetacardType().getName()));
      processMetacard(update.getOldMetacard(), this::readQueryMetacardAndCancelSchedule)
          .elseDo(error -> errors.add(ImmutablePair.of(update.getOldMetacard(), error)));
      // TODO TEMP
      LOGGER.warn(
          String.format(
              "Processing new metacard of type %s...",
              update.getNewMetacard().getMetacardType().getName()));
      processMetacard(update.getNewMetacard(), this::readQueryMetacardAndSchedule)
          .elseDo(error -> errors.add(ImmutablePair.of(update.getNewMetacard(), error)));
    }

    throwErrorsIfAny(errors);

    return updates;
  }

  @Override
  public DeleteResponse process(DeleteResponse deletion) throws PluginExecutionException {
    // TODO TEMP
    LOGGER.warn("Processing deletion...");
    return processSingularResponse(
        deletion, deletion.getDeletedMetacards(), this::readQueryMetacardAndCancelSchedule);
  }
}
