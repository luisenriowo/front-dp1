// Tipos y datos para la simulación de Tasf.B2B

export type Continent = "America" | "Asia" | "Europe"

export type CollapseReason = "overflow" | "storm" | "strike" | "technical"
export type CollapseSeverity = "mild" | "critical" | "catastrophic"

export interface CollapseEvent {
  id: string
  airportId: string
  reason: CollapseReason
  severity: CollapseSeverity
  startDay: number
  startHour: number
  cancelledFlightIds: string[]
  affectedShipmentCount: number
  resolved: boolean
}

export interface Airport {
  id: string
  city: string
  code: string
  continent: Continent
  coordinates: [number, number] // [longitud, latitud] para el mapa real
  storageCapacity: number // 500-800 maletas
  currentStorage: number
}

export interface Flight {
  id: string
  origin: string
  destination: string
  capacity: number // 150-250 mismo continente, 150-400 diferente continente
  departureTime: number // hora del día (0-23)
  duration: number // 0.5 días mismo continente, 1 día diferente
  currentLoad: number
}

export interface Shipment {
  id: string
  origin: string
  destination: string
  quantity: number
  createdAt: Date
  deadline: Date // 1 día mismo continente, 2 días diferente
  currentLocation: string
  status: "pending" | "in-transit" | "delivered" | "delayed"
  route: string[]
  currentRouteIndex: number
}

export interface SimulationState {
  day: number
  timeOfDay: number // 0-23
  airports: Airport[]
  flights: Flight[]
  shipments: Shipment[]
  metrics: SimulationMetrics
}

export interface SimulationMetrics {
  totalShipments: number
  deliveredOnTime: number
  deliveredLate: number
  inTransit: number
  pending: number
  averageDeliveryTime: number
  warehouseUtilization: Record<string, number>
}

// Aeropuertos con coordenadas geográficas reales [longitud, latitud]
export const initialAirports: Airport[] = [
  // América
  { id: "nyc", city: "Nueva York", code: "JFK", continent: "America", coordinates: [-73.7781, 40.6413], storageCapacity: 750, currentStorage: 0 },
  { id: "mia", city: "Miami", code: "MIA", continent: "America", coordinates: [-80.2870, 25.7959], storageCapacity: 600, currentStorage: 0 },
  { id: "lax", city: "Los Ángeles", code: "LAX", continent: "America", coordinates: [-118.4085, 33.9416], storageCapacity: 700, currentStorage: 0 },
  { id: "mex", city: "Ciudad de México", code: "MEX", continent: "America", coordinates: [-99.0721, 19.4363], storageCapacity: 650, currentStorage: 0 },
  { id: "bog", city: "Bogotá", code: "BOG", continent: "America", coordinates: [-74.1469, 4.7016], storageCapacity: 550, currentStorage: 0 },
  { id: "lim", city: "Lima", code: "LIM", continent: "America", coordinates: [-77.1143, -12.0219], storageCapacity: 500, currentStorage: 0 },
  { id: "gru", city: "São Paulo", code: "GRU", continent: "America", coordinates: [-46.4730, -23.4356], storageCapacity: 800, currentStorage: 0 },
  
  // Europa
  { id: "lhr", city: "Londres", code: "LHR", continent: "Europe", coordinates: [-0.4543, 51.4700], storageCapacity: 800, currentStorage: 0 },
  { id: "cdg", city: "París", code: "CDG", continent: "Europe", coordinates: [2.5479, 49.0097], storageCapacity: 750, currentStorage: 0 },
  { id: "fra", city: "Frankfurt", code: "FRA", continent: "Europe", coordinates: [8.5622, 50.0379], storageCapacity: 700, currentStorage: 0 },
  { id: "mad", city: "Madrid", code: "MAD", continent: "Europe", coordinates: [-3.5673, 40.4983], storageCapacity: 650, currentStorage: 0 },
  { id: "fco", city: "Roma", code: "FCO", continent: "Europe", coordinates: [12.2389, 41.8003], storageCapacity: 600, currentStorage: 0 },
  
  // Asia
  { id: "nrt", city: "Tokio", code: "NRT", continent: "Asia", coordinates: [140.3929, 35.7720], storageCapacity: 800, currentStorage: 0 },
  { id: "pek", city: "Beijing", code: "PEK", continent: "Asia", coordinates: [116.5975, 40.0799], storageCapacity: 750, currentStorage: 0 },
  { id: "pvg", city: "Shanghái", code: "PVG", continent: "Asia", coordinates: [121.8058, 31.1443], storageCapacity: 700, currentStorage: 0 },
  { id: "sin", city: "Singapur", code: "SIN", continent: "Asia", coordinates: [103.9915, 1.3644], storageCapacity: 700, currentStorage: 0 },
  { id: "bkk", city: "Bangkok", code: "BKK", continent: "Asia", coordinates: [100.7501, 13.6900], storageCapacity: 600, currentStorage: 0 },
  { id: "dxb", city: "Dubái", code: "DXB", continent: "Asia", coordinates: [55.3644, 25.2532], storageCapacity: 750, currentStorage: 0 },
]

// Vuelos de ejemplo
export const initialFlights: Flight[] = [
  // Vuelos América
  { id: "f1", origin: "nyc", destination: "mia", capacity: 200, departureTime: 8, duration: 0.5, currentLoad: 0 },
  { id: "f2", origin: "nyc", destination: "lax", capacity: 220, departureTime: 10, duration: 0.5, currentLoad: 0 },
  { id: "f3", origin: "lax", destination: "mex", capacity: 180, departureTime: 12, duration: 0.5, currentLoad: 0 },
  { id: "f4", origin: "mia", destination: "bog", capacity: 160, departureTime: 14, duration: 0.5, currentLoad: 0 },
  { id: "f5", origin: "bog", destination: "lim", capacity: 150, departureTime: 16, duration: 0.5, currentLoad: 0 },
  { id: "f6", origin: "lim", destination: "gru", capacity: 170, departureTime: 8, duration: 0.5, currentLoad: 0 },
  { id: "f7", origin: "gru", destination: "mia", capacity: 200, departureTime: 6, duration: 0.5, currentLoad: 0 },
  
  // Vuelos Europa
  { id: "f8", origin: "lhr", destination: "cdg", capacity: 200, departureTime: 7, duration: 0.5, currentLoad: 0 },
  { id: "f9", origin: "cdg", destination: "fra", capacity: 180, departureTime: 9, duration: 0.5, currentLoad: 0 },
  { id: "f10", origin: "fra", destination: "fco", capacity: 170, departureTime: 11, duration: 0.5, currentLoad: 0 },
  { id: "f11", origin: "mad", destination: "lhr", capacity: 190, departureTime: 13, duration: 0.5, currentLoad: 0 },
  { id: "f12", origin: "fco", destination: "mad", capacity: 160, departureTime: 15, duration: 0.5, currentLoad: 0 },
  
  // Vuelos Asia
  { id: "f13", origin: "nrt", destination: "pek", capacity: 220, departureTime: 8, duration: 0.5, currentLoad: 0 },
  { id: "f14", origin: "pek", destination: "pvg", capacity: 200, departureTime: 10, duration: 0.5, currentLoad: 0 },
  { id: "f15", origin: "pvg", destination: "sin", capacity: 180, departureTime: 12, duration: 0.5, currentLoad: 0 },
  { id: "f16", origin: "sin", destination: "bkk", capacity: 170, departureTime: 14, duration: 0.5, currentLoad: 0 },
  { id: "f17", origin: "bkk", destination: "dxb", capacity: 190, departureTime: 16, duration: 0.5, currentLoad: 0 },
  { id: "f18", origin: "dxb", destination: "nrt", capacity: 250, departureTime: 18, duration: 0.5, currentLoad: 0 },
  
  // Vuelos intercontinentales
  { id: "f19", origin: "nyc", destination: "lhr", capacity: 350, departureTime: 20, duration: 1, currentLoad: 0 },
  { id: "f20", origin: "lhr", destination: "nyc", capacity: 350, departureTime: 10, duration: 1, currentLoad: 0 },
  { id: "f21", origin: "lax", destination: "nrt", capacity: 300, departureTime: 22, duration: 1, currentLoad: 0 },
  { id: "f22", origin: "nrt", destination: "lax", capacity: 300, departureTime: 14, duration: 1, currentLoad: 0 },
  { id: "f23", origin: "lhr", destination: "dxb", capacity: 280, departureTime: 8, duration: 1, currentLoad: 0 },
  { id: "f24", origin: "dxb", destination: "lhr", capacity: 280, departureTime: 20, duration: 1, currentLoad: 0 },
  { id: "f25", origin: "fra", destination: "pvg", capacity: 320, departureTime: 12, duration: 1, currentLoad: 0 },
  { id: "f26", origin: "pvg", destination: "fra", capacity: 320, departureTime: 2, duration: 1, currentLoad: 0 },
  { id: "f27", origin: "cdg", destination: "gru", capacity: 300, departureTime: 22, duration: 1, currentLoad: 0 },
  { id: "f28", origin: "gru", destination: "cdg", capacity: 300, departureTime: 10, duration: 1, currentLoad: 0 },
  { id: "f29", origin: "mia", destination: "mad", capacity: 280, departureTime: 18, duration: 1, currentLoad: 0 },
  { id: "f30", origin: "mad", destination: "mia", capacity: 280, departureTime: 8, duration: 1, currentLoad: 0 },
]

export function getStatusColor(percentage: number): "green" | "amber" | "red" {
  if (percentage < 60) return "green"
  if (percentage < 85) return "amber"
  return "red"
}

export function getDeliveryStatus(shipment: Shipment, currentDate: Date): "green" | "amber" | "red" {
  const timeRemaining = shipment.deadline.getTime() - currentDate.getTime()
  const totalTime = shipment.deadline.getTime() - shipment.createdAt.getTime()
  const percentageUsed = ((totalTime - timeRemaining) / totalTime) * 100
  
  if (shipment.status === "delivered") return "green"
  if (shipment.status === "delayed") return "red"
  if (percentageUsed > 80) return "amber"
  return "green"
}

export function generateRandomShipments(
  airports: Airport[],
  count: number,
  day: number
): Shipment[] {
  const shipments: Shipment[] = []
  
  for (let i = 0; i < count; i++) {
    const originIdx = Math.floor(Math.random() * airports.length)
    let destIdx = Math.floor(Math.random() * airports.length)
    while (destIdx === originIdx) {
      destIdx = Math.floor(Math.random() * airports.length)
    }
    
    const origin = airports[originIdx]
    const destination = airports[destIdx]
    const sameCont = origin.continent === destination.continent
    const deadlineDays = sameCont ? 1 : 2
    
    const createdAt = new Date(2026, 2, 31 + day)
    const deadline = new Date(createdAt)
    deadline.setDate(deadline.getDate() + deadlineDays)
    
    shipments.push({
      id: `s-${day}-${i}`,
      origin: origin.id,
      destination: destination.id,
      quantity: Math.floor(Math.random() * 10) + 1,
      createdAt,
      deadline,
      currentLocation: origin.id,
      status: "pending",
      route: [origin.id, destination.id],
      currentRouteIndex: 0,
    })
  }
  
  return shipments
}
