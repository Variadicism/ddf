/**
 * Copyright (c) Connexta, LLC
 *
 * <p>Unlimited Government Rights (FAR Subpart 27.4) Government right to use, disclose, reproduce,
 * prepare derivative works, distribute copies to the public, and perform and display publicly, in
 * any manner and for any purpose, and to have or permit others to do so.
 */
package org.codice.ddf.platform.ignite;

import static java.util.Optional.ofNullable;

import java.io.PrintStream;
import java.util.Arrays;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;
import java.util.stream.Collectors;
import javax.cache.Cache;
import org.apache.commons.lang.StringUtils;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.osgi.IgniteAbstractOsgiContextActivator;
import org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi;
import org.apache.ignite.spi.discovery.tcp.ipfinder.vm.TcpDiscoveryVmIpFinder;
import org.apache.ignite.ssl.SslContextFactory;
import org.codice.ddf.configuration.PropertyResolver;
import org.jetbrains.annotations.Nullable;
import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Begins the process of starting Ignite inside OSGi */
public class IgniteIgniter extends IgniteAbstractOsgiContextActivator {
  private static final Logger LOGGER = LoggerFactory.getLogger(IgniteIgniter.class);

  @Override
  public IgniteConfiguration igniteConfiguration() {
    final PrintStream oldSysOut = System.out;
    System.setOut(
        new PrintStream(oldSysOut) {
          final Logger igniteStdoutLogger = LoggerFactory.getLogger("ignite");

          @Override
          public void print(String message) {
            if (Thread.currentThread()
                .getStackTrace()[2]
                .getClassName()
                .startsWith("org.apache.ignite")) {
              igniteStdoutLogger.info(message);
            } else {
              super.print(message);
            }
          }
        });

    IgniteConfiguration configuration = new IgniteConfiguration();

    final SslContextFactory factory = new SslContextFactory();
    factory.setKeyStoreFilePath(System.getProperty("javax.net.ssl.keyStore"));
    factory.setKeyStorePassword(
        ofNullable(System.getProperty("javax.net.ssl.keyStorePassword"))
            .map(String::toCharArray)
            .orElse(null));
    factory.setTrustStoreFilePath(System.getProperty("javax.net.ssl.trustStore"));
    factory.setTrustStorePassword(
        ofNullable(System.getProperty("javax.net.ssl.trustStorePassword"))
            .map(String::toCharArray)
            .orElse(null));
    factory.setProtocol("TLS");
    configuration.setSslContextFactory(factory);

    final String hostsString =
        PropertyResolver.resolveProperties(System.getProperty("ignite.hosts"));
    if (hostsString == null) {
      LOGGER.debug(
          "The Ignite hosts could not be retrieved from the ignite.hosts system property.");
    } else {
      final List<String> hosts =
          Arrays.stream(hostsString.split(","))
              .filter(StringUtils::isNotBlank)
              .map(String::trim)
              .map(hostAddress -> hostAddress.equals("0.0.0.0") ? "127.0.0.1" : hostAddress)
              .collect(Collectors.toList());
      final TcpDiscoveryVmIpFinder ipFinder = new TcpDiscoveryVmIpFinder();
      ipFinder.setAddresses(hosts);
      final TcpDiscoverySpi spi = new TcpDiscoverySpi();
      spi.setIpFinder(ipFinder);
      configuration.setDiscoverySpi(spi);
    }

    configuration.setMetricsLogFrequency(0);

    return configuration;
  }

  @Override
  protected void onAfterStart(BundleContext context, @Nullable Throwable throwable) {
    super.onAfterStart(context, throwable);
    ignite.cluster().active(true);
    ignite.cacheNames().forEach(cacheName -> registerCache(context, ignite.cache(cacheName)));
  }

  private void registerCache(BundleContext context, Cache cache) {
    final Dictionary<String, String> props = new Hashtable<>();
    props.put("name", cache.getName());

    context.registerService(Cache.class, cache, props);
  }
}
