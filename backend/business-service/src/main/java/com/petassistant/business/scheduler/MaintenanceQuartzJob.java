package com.petassistant.business.scheduler;

import com.petassistant.business.service.ScheduledMaintenanceService;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.quartz.QuartzJobBean;

/** Quartz 的轻量入口；具体业务、重试、锁和审计均委托给 service 层。 */
@DisallowConcurrentExecution
public class MaintenanceQuartzJob extends QuartzJobBean {

    @Autowired
    private ScheduledMaintenanceService maintenanceService;

    @Override
    protected void executeInternal(JobExecutionContext context) {
        maintenanceService.execute(context.getMergedJobDataMap().getString("task"));
    }
}
