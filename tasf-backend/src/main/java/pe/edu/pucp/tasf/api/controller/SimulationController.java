package pe.edu.pucp.tasf.api.controller;

import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.edu.pucp.tasf.api.dto.AirportResponse;
import pe.edu.pucp.tasf.api.dto.CollapseEventResponse;
import pe.edu.pucp.tasf.api.dto.FlightResponse;
import pe.edu.pucp.tasf.api.dto.ShipmentResponse;
import pe.edu.pucp.tasf.api.dto.SimulationMetricsResponse;
import pe.edu.pucp.tasf.api.dto.SimulationStateResponse;
import pe.edu.pucp.tasf.service.ApiMapper;
import pe.edu.pucp.tasf.service.SimulationDataService;

@RestController
@RequestMapping("/simulation")
public class SimulationController {

    private final SimulationDataService simulationDataService;
    private final ApiMapper apiMapper;

    public SimulationController(SimulationDataService simulationDataService, ApiMapper apiMapper) {
        this.simulationDataService = simulationDataService;
        this.apiMapper = apiMapper;
    }

    @GetMapping("/state")
    public SimulationStateResponse getState() {
        List<AirportResponse> airports = simulationDataService.getAirports().stream()
            .map(apiMapper::toAirportResponse)
            .toList();
        List<FlightResponse> flights = simulationDataService.getFlights().stream()
            .map(apiMapper::toFlightResponse)
            .toList();
        List<ShipmentResponse> shipments = simulationDataService.getShipments().stream()
            .map(apiMapper::toShipmentResponse)
            .toList();
        SimulationMetricsResponse metrics = apiMapper.toMetricsResponse(
            simulationDataService.getShipments(),
            simulationDataService.getAirports().stream().toList()
        );
        return new SimulationStateResponse(
            simulationDataService.getSimulationDay(),
            simulationDataService.getSimulationTimeOfDay(),
            airports,
            flights,
            shipments,
            metrics
        );
    }

    @GetMapping("/metrics")
    public SimulationMetricsResponse getMetrics() {
        return apiMapper.toMetricsResponse(
            simulationDataService.getShipments(),
            simulationDataService.getAirports().stream().toList()
        );
    }

    @GetMapping("/collapse-events")
    public List<CollapseEventResponse> getCollapseEvents() {
        return simulationDataService.getCollapseEvents();
    }

    @PostMapping("/start")
    public ResponseEntity<Void> start() {
        simulationDataService.start();
        return ResponseEntity.ok().build();
    }

    @PostMapping("/pause")
    public ResponseEntity<Void> pause() {
        simulationDataService.pause();
        return ResponseEntity.ok().build();
    }

    @PostMapping("/reset")
    public ResponseEntity<Void> reset() {
        simulationDataService.reset();
        return ResponseEntity.ok().build();
    }
}
