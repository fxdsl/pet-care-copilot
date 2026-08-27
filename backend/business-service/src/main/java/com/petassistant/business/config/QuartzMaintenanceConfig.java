package com.petassistant.business.config;

import java.util.TimeZone;

import com.petassistant.business.scheduler.MaintenanceQuartzJob;
import org.quartz.CronScheduleBuilder;
import org.quartz.JobBuilder;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.boot.autoconfigure.quartz.SchedulerFactoryBeanCustomizer;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.quartz.SpringBeanJobFactory;

/** 第十三周 Quartz 调度表；Cron 可由环境变量覆盖，默认按 Asia/Shanghai 执行。 */
@Configuration
public class QuartzMaintenanceConfig {

    private static final TimeZone ZONE = TimeZone.getTimeZone("Asia/Shanghai");

    @Bean
    public AutowiringJobFactory autowiringJobFactory(AutowireCapableBeanFactory beanFactory) {
        return new AutowiringJobFactory(beanFactory);
    }

    @Bean
    public SchedulerFactoryBeanCustomizer quartzJobFactoryCustomizer(AutowiringJobFactory jobFactory) {
        return schedulerFactory -> schedulerFactory.setJobFactory(jobFactory);
    }

    @Bean("trendSnapshotJob")
    public JobDetail trendSnapshotJob() { return job("trend-snapshot", "TREND_SNAPSHOT"); }

    @Bean("knowledgeExpiryJob")
    public JobDetail knowledgeExpiryJob() { return job("knowledge-expiry", "KNOWLEDGE_EXPIRY"); }

    @Bean("outboxRecoveryJob")
    public JobDetail outboxRecoveryJob() { return job("outbox-recovery", "OUTBOX_RECOVERY"); }

    @Bean("auditArchiveJob")
    public JobDetail auditArchiveJob() { return job("audit-archive", "AUDIT_ARCHIVE"); }

    @Bean("queueBacklogJob")
    public JobDetail queueBacklogJob() { return job("queue-backlog", "QUEUE_BACKLOG"); }

    @Bean
    public Trigger trendSnapshotTrigger(@Qualifier("trendSnapshotJob") JobDetail job) {
        return trigger("trend-snapshot", job, "0 0/15 * * * ?");
    }

    @Bean
    public Trigger knowledgeExpiryTrigger(@Qualifier("knowledgeExpiryJob") JobDetail job) {
        return trigger("knowledge-expiry", job, "0 5 * * * ?");
    }

    @Bean
    public Trigger outboxRecoveryTrigger(@Qualifier("outboxRecoveryJob") JobDetail job) {
        return trigger("outbox-recovery", job, "0 0/5 * * * ?");
    }

    @Bean
    public Trigger auditArchiveTrigger(@Qualifier("auditArchiveJob") JobDetail job) {
        return trigger("audit-archive", job, "0 20 3 * * ?");
    }

    @Bean
    public Trigger queueBacklogTrigger(@Qualifier("queueBacklogJob") JobDetail job) {
        return trigger("queue-backlog", job, "0 0/1 * * * ?");
    }

    private static JobDetail job(String identity, String task) {
        JobDataMap data = new JobDataMap();
        data.put("task", task);
        return JobBuilder.newJob(MaintenanceQuartzJob.class)
                .withIdentity(identity, "platform-maintenance")
                .usingJobData(data)
                .storeDurably()
                .build();
    }

    private static Trigger trigger(String identity, JobDetail job, String cron) {
        return TriggerBuilder.newTrigger()
                .withIdentity(identity + "-trigger", "platform-maintenance")
                .forJob(job)
                .withSchedule(CronScheduleBuilder.cronSchedule(cron)
                        .inTimeZone(ZONE)
                        .withMisfireHandlingInstructionDoNothing())
                .build();
    }

    /** Quartz 自己创建 Job 实例，因此在创建后交回 Spring 完成 @Autowired 字段注入。 */
    public static final class AutowiringJobFactory extends SpringBeanJobFactory
            implements ApplicationContextAware {

        private final AutowireCapableBeanFactory beanFactory;

        public AutowiringJobFactory(AutowireCapableBeanFactory beanFactory) {
            this.beanFactory = beanFactory;
        }

        @Override
        protected Object createJobInstance(org.quartz.spi.TriggerFiredBundle bundle) throws Exception {
            Object job = super.createJobInstance(bundle);
            beanFactory.autowireBean(job);
            return job;
        }

        @Override
        public void setApplicationContext(ApplicationContext applicationContext) {
            // BeanFactory 已由构造器注入；保留接口实现让 Spring 明确感知该 JobFactory。
        }
    }
}
