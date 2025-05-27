package ch.admin.bit.jeap.reaction.observer.web.api;

import ch.admin.bit.jeap.reaction.observer.domain.aggregation.AggregationService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/management")
@RequiredArgsConstructor
@Slf4j
public class ManagementController {

    private final AggregationService aggregationService;

    @PreAuthorize("hasAnyRole('reaction-observer-write')")
    @Operation(summary = "Aggregate data for a date in format yyyy-mm-dd.")
    @GetMapping("/aggregate-data/{date}")
    public String aggregateData(@PathVariable String date) {
        try {
            aggregationService.aggregateData(LocalDate.parse(date));
            log.info("Data aggregation completed for date: {}", date);
            return "Data aggregation completed";
        } catch (Exception e) {
            log.error("Error aggregating data for date {}: {}", date, e.getMessage());
            return "Error aggregating data: " + e.getMessage();
        }
    }

}
