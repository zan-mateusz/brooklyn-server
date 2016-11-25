package io.cloudsoft.amp.container.kubernetes.location;

import java.util.List;
import java.util.Map;

import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.location.LocationConfigKeys;
import org.apache.brooklyn.core.location.cloud.CloudLocationConfig;
import org.apache.brooklyn.util.core.flags.SetFromFlag;

import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableMap;
import com.google.common.reflect.TypeToken;

public interface KubernetesLocationConfig extends CloudLocationConfig {

    @SetFromFlag("endpoint")
    ConfigKey<String> MASTER_URL = LocationConfigKeys.CLOUD_ENDPOINT;

    @SetFromFlag("caCertUrl")
    ConfigKey<String> CA_CERT = ConfigKeys.builder(String.class)
            .name("kubernetes.caCert")
            .description("URL of resource containing CA certificate data")
            .constraint(Predicates.<String>notNull())
            .build();

    @SetFromFlag("clientCertUrl")
    ConfigKey<String> CLIENT_CERT = ConfigKeys.builder(String.class)
            .name("kubernetes.clientCert")
            .description("URL of resource containing client certificate data")
            .constraint(Predicates.<String>notNull())
            .build();

    @SetFromFlag("clientKeyUrl")
    ConfigKey<String> CLIENT_KEY = ConfigKeys.builder(String.class)
            .name("kubernetes.clientKey")
            .description("URL of resource containing client key data")
            .constraint(Predicates.<String>notNull())
            .build();

    @SetFromFlag("oauthToken")
    ConfigKey<String> OAUTH_TOKEN = ConfigKeys.builder(String.class)
            .name("kubernetes.oauthToken")
            .description("The OAuth token data for the current user")
            .constraint(Predicates.<String>notNull())
            .build();

    @SetFromFlag("namespace")
    ConfigKey<String> NAMESPACE = ConfigKeys.builder(String.class)
            .name("kubernetes.namespace")
            .description("Namespace where resources will live; the default is 'amp'")
            .defaultValue("amp")
            .constraint(Predicates.<String>notNull())
            .build();

    @SetFromFlag("persistentVolumes")
    @SuppressWarnings("serial")
    ConfigKey<List<String>> PERSISTENT_VOLUMES = ConfigKeys.builder(new TypeToken<List<String>>() {})
            .name("kubernetes.persistent.volumes")
            .description("Set up persistent volumes.")
            .constraint(Predicates.<List<String>>notNull())
            .build();

    @SetFromFlag("deployment")
    ConfigKey<String> DEPLOYMENT = ConfigKeys.builder(String.class)
            .name("kubernetes.deployment")
            .description("Deployment where resources will live.")
            .constraint(Predicates.<String>notNull())
            .build();

    @SetFromFlag("service")
    ConfigKey<String> SERVICE = ConfigKeys.builder(String.class)
            .name("kubernetes.service")
            .description("Service that exposes the deployment.")
            .constraint(Predicates.<String>notNull())
            .build();

    @SetFromFlag("image")
    ConfigKey<String> IMAGE = ConfigKeys.builder(String.class)
            .name("kubernetes.image")
            .description("Docker image to be deployed into the pod")
            .constraint(Predicates.<String>notNull())
            .build();

    @SetFromFlag("osFamily")
    ConfigKey<String> OS_FAMILY = ConfigKeys.builder(String.class)
            .name("kubernetes.osFamily")
            .description("OS family, e.g. CentOS, Ubuntu")
            .build();
    
    @SetFromFlag("osVersionRegex")
    ConfigKey<String> OS_VERSION_REGEX = ConfigKeys.builder(String.class)
            .name("kubernetes.osVersionRegex")
            .description("Regular expression for the OS version to load")
            .build();

    @SuppressWarnings("serial")
    public static final ConfigKey<Map<String, ?>> ENV = ConfigKeys.newConfigKey(
            new TypeToken<Map<String, ?>>() {},
            "env", 
            "Environment variables to inject when starting the container", 
            ImmutableMap.<String, Object>of());

    @SetFromFlag("replicas")
    ConfigKey<Integer> REPLICAS = ConfigKeys.builder(Integer.class)
            .name("kubernetes.replicas")
            .description("Number of replicas into the pod")
            .constraint(Predicates.notNull())
            .defaultValue(1)
            .build();

    @SetFromFlag("secrets")
    @SuppressWarnings("serial")
    ConfigKey<Map<String, String>> SECRETS = ConfigKeys.builder(
            new TypeToken<Map<String, String>>() {})
            .name("kubernetes.secrets")
            .description("Kubernetes secrets to be added to the pod")
            .build();

    @SetFromFlag("limits")
    @SuppressWarnings("serial")
    ConfigKey<Map<String, String>> LIMITS = ConfigKeys.builder(
            new TypeToken<Map<String, String>>() {})
            .name("kubernetes.limits")
            .description("Kubernetes resource limits")
            .build();

    @SetFromFlag("privileged")
    ConfigKey<Boolean> PRIVILEGED = ConfigKeys.builder(Boolean.class)
            .name("kubernetes.privileged")
            .description("Kubernetes resource limits")
            .defaultValue(false)
            .build();

    ConfigKey<KubernetesClientRegistry> KUBERNETES_CLIENT_REGISTRY = ConfigKeys.builder(KubernetesClientRegistry.class) 
            .name("kubernetesClientRegistry")
            .description("Registry/Factory for creating Kubernetes client; default is almost always fine, "
                    + "except where tests want to customize behaviour") 
            .defaultValue(KubernetesClientRegistryImpl.INSTANCE)
            .build();

    ConfigKey<String> LOGIN_USER = ConfigKeys.builder(String.class)
            .name("kubernetes.loginUser")
            .description("Override the user who logs in initially to perform setup")
            .defaultValue("root")
            .constraint(Predicates.<String>notNull())
            .build();

    @SetFromFlag("loginUserPassword")
    ConfigKey<String> LOGIN_USER_PASSWORD = ConfigKeys.builder(String.class)
            .name("kubernetes.loginUser.password")
            .description("Custom password for the user who logs in initially")
            .constraint(Predicates.<String>notNull())
            .build();
    
    public static final ConfigKey<Boolean> INJECT_LOGIN_CREDENTIAL = ConfigKeys.builder(Boolean.class)
            .name("injectLoginCredential") 
            .description("Whether to inject login credentials (if null, will infer from image choice); ignored if explicit 'loginUser.password' supplied")
            .build();
}

