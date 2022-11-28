package com.example.skywalking;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.observation.MeterObservationHandler;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import jakarta.annotation.PreDestroy;
import jakarta.servlet.DispatcherType;
import java.util.List;
import org.apache.skywalking.apm.meter.micrometer.SkywalkingMeterRegistry;
import org.apache.skywalking.apm.meter.micrometer.observation.SkywalkingDefaultTracingHandler;
import org.apache.skywalking.apm.meter.micrometer.observation.SkywalkingMeterHandler;
import org.apache.skywalking.apm.meter.micrometer.observation.SkywalkingReceiverTracingHandler;
import org.apache.skywalking.apm.meter.micrometer.observation.SkywalkingSenderTracingHandler;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.filter.ServerHttpObservationFilter;

@SpringBootApplication
public class SkywalkingApplication {

	public static void main(String[] args) {
		SpringApplication.run(SkywalkingApplication.class, args);
	}

}


@Configuration
@Profile("client")
class ClientConfig {

	@Bean
	CommandLineRunner commandLineRunner(RestTemplate restTemplate) {
		return args -> {
			System.out.println(restTemplate.getForObject("http://localhost:8080/foo", String.class));
		};
	}

	@Bean
	public RestTemplate restTemplate(ObservationRegistry observationRegistry) {
		RestTemplate restTemplate = new RestTemplate();
		restTemplate.setObservationRegistry(observationRegistry);
		return restTemplate;
	}


}

@Configuration
@Profile("server")
class ServerConfig {

	@Bean
	FilterRegistrationBean testServerHttpObservationFilter(ObservationRegistry observationRegistry) {
		FilterRegistrationBean servletRegistrationBean = new FilterRegistrationBean(new ServerHttpObservationFilter(observationRegistry));
		servletRegistrationBean.setAsyncSupported(true);
		servletRegistrationBean.setDispatcherTypes(DispatcherType.ASYNC, DispatcherType.ERROR, DispatcherType.FORWARD, DispatcherType.INCLUDE);
		return servletRegistrationBean;
	}

}

@Profile("server")
@RestController
class ServerController {

	@GetMapping("/foo")
	String foo(@RequestHeader HttpHeaders httpHeaders) {
		System.out.println("Got following headers [" + httpHeaders + "]");
		return "Hello back";
	}
}

@Configuration
class CommonConfig {

	@Configuration(proxyBeanMethods = false)
	static class TestObservationConfiguration {

		@Bean
		ObservationRegistry testObservationRegistry(List<MeterObservationHandler<?>> handlers) {
			ObservationRegistry registry = ObservationRegistry.create();
			registry.observationConfig().observationHandler(new ObservationHandler.FirstMatchingCompositeObservationHandler(handlers));
			registry.observationConfig().observationHandler(new ObservationHandler.FirstMatchingCompositeObservationHandler(new SkywalkingSenderTracingHandler(), new SkywalkingReceiverTracingHandler(), new SkywalkingDefaultTracingHandler()));
			return registry;
		}

		@Bean
		SkywalkingMeterRegistry skyWalkingMeterRegistry() {
			return new SkywalkingMeterRegistry();
		}

		@Bean
		MeterObservationHandler<?> meterObservationHandler(SkywalkingMeterRegistry skywalkingMeterRegistry) {
			return new SkywalkingMeterHandler(skywalkingMeterRegistry);
		}

		@Bean
		MetricsDumper metricsDumper(MeterRegistry meterRegistry) {
			return new MetricsDumper(meterRegistry);
		}

		static class MetricsDumper {
			private final MeterRegistry meterRegistry;

			MetricsDumper(MeterRegistry meterRegistry) {
				this.meterRegistry = meterRegistry;
			}

			@PreDestroy
			void dumpMetrics() {
				System.out.println("==== METRICS ====");
				this.meterRegistry.getMeters().forEach(meter -> System.out.println(" - Metric type \t[" + meter.getId().getType() + "],\tname [" + meter.getId().getName() + "],\ttags " + meter.getId().getTags() + ",\tmeasurements " + meter.measure()));
				System.out.println("=================");
			}
		}
	}
}
