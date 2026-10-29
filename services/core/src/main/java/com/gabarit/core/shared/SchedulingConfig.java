package com.gabarit.core.shared;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Jobs agendados do core (@Scheduled), ex.: resumo diário de concursos. Com mais de uma instância
 * do core, todas rodam o job: cada job tem de ser seguro para isso (ver NotificationDigestJob).
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class SchedulingConfig {

}
