package org.semtec.one.one;

import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.rdf.model.*;
import org.apache.jena.vocabulary.*;

import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Exercise 2 – Airline RDF/RDFS Ontology
 *
 * Models an airline that operates at four airports (BER, JFK, FCO, LHR),
 * offers connections between all six airport pairs, and records customer
 * trip bookings composed of individually booked, sequentially connected flights.
 *
 * Design decisions:
 *   - Connections are bidirectional resources (one per unordered airport pair).
 *     The actual travel direction per flight is captured via the
 *     departureAirport / arrivalAirport object properties on each Flight,
 *     keeping connection data (duration, aircraft, capacity) in one place.
 *
 *   - Flight ordering within a trip is modelled as a singly-linked list using
 *     airline:nextFlight.  This makes the "connected in sequence" constraint
 *     structurally explicit in the graph rather than relying on a numeric index.
 *
 *   - airline:partOfTrip back-links every Flight to its Trip, enabling direct
 *     flight-to-trip lookup without traversing the full chain (supports query c).
 *
 *   - xs:dateTime is used for all timestamps; xs:integer for counts and durations
 *     (minutes); xs:gYear for year-only values; xs:decimal for prices.
 *
 *   - Literals are used for atomic scalar values (IATA code, manager name, seat
 *     label, class name) where a separate resource would add no useful structure.
 *
 * Self-control queries supported by this model:
 *   a) Berlin–Rome flight: query flights where departureAirport = airline:BER and
 *      arrivalAirport = airline:FCO (or vice-versa) and partOfTrip = <trip>.
 *      Trip 1 (, BER → FCO → LHR → JFK → BER) satisfies this.
 *   b) Time elapsed after each flight: compute flightEndTime − tripStartTime for
 *      each flight of a trip using the explicit xs:dateTime literals.
 *   c) Who booked the trip: trip → airline:bookedBy → airline:Customer →
 *      airline:customerName.
 */
public class Main {

    /** Base namespace for all ontology terms defined in this model. */
    static final String NS = "https://wont-crash.de/trust-me/airline#";

    public static void main(String[] args) throws IOException {
        Model model = ModelFactory.createDefaultModel();

        // Register namespace prefixes for readable serialisation output
        model.setNsPrefix("airline", NS);
        model.setNsPrefix("rdf",     RDF.getURI());
        model.setNsPrefix("rdfs",    RDFS.getURI());
        model.setNsPrefix("xsd",     XSDDatatype.XSD + "#");

        // Build the model in thematic sections
        defineSchema(model);

        Resource[] airports    = createAirports(model);
        Resource[] connections = createConnections(model, airports);
        createTrips(model, airports, connections);

        // Serialise to RDF/XML on stdout and write to file
        model.write(System.out, "RDF/XML-ABBREV");
        try (FileOutputStream fos = new FileOutputStream("E2.rdf")) {
            model.write(fos, "RDF/XML-ABBREV");
        }
        System.err.println("Serialised ontology to E2.rdf");
    }

    // =========================================================================
    // SCHEMA – Classes and Properties
    // =========================================================================

    /**
     * Defines all RDFS classes and properties with domain, range, rdfs:label,
     * and rdfs:comment so that the ontology is self-describing.
     * Properties are grouped thematically by the class they describe.
     */
    static void defineSchema(Model model) {

        // ---- Classes --------------------------------------------------------

        Resource Airport    = cls(model, "Airport",
                "An airport at which the airline operates.");
        Resource Connection = cls(model, "Connection",
                "A bidirectional scheduled route between two airports. " +
                "One resource per unordered pair; direction is resolved per Flight.");
        Resource Customer   = cls(model, "Customer",
                "A passenger who books trips with the airline.");
        Resource Trip       = cls(model, "Trip",
                "A complete booking consisting of one or more sequential flights.");
        Resource Flight     = cls(model, "Flight",
                "A single leg within a trip, travelling along a Connection in one direction.");

        // ---- Airport properties --------------------------------------------
        // IATA codes and city names are plain strings,
        // employee counts and years are numeric values with no further structure.
        prop(model, "iataCode",              Airport, XSD.xstring,
                "Three-letter IATA airport code.");
        prop(model, "locatedInCity",         Airport, XSD.xstring,
                "Name of the city in which the airport is located.");
        prop(model, "numberOfEmployees",     Airport, XSD.integer,
                "Number of airline employees stationed at this airport.");
        prop(model, "managerInCharge",       Airport, XSD.xstring,
                "Full name of the airline manager responsible for this airport.");
        prop(model, "yearOfOperationsStart", Airport, XSD.gYear,
                "Year in which the airline began operations at this airport.");

        // ---- Connection properties -----------------------------------------
        // Two symmetric endpoint properties are used instead of an ordered pair
        // so connections can be traversed in both directions without directionality bias.
        objProp(model, "connectsAirport1", Connection, Airport,
                "First endpoint of the bidirectional connection.");
        objProp(model, "connectsAirport2", Connection, Airport,
                "Second endpoint of the bidirectional connection.");
        prop(model, "flightDurationMinutes", Connection, XSD.integer,
                "Scheduled flight duration in minutes.");
        prop(model, "planeModel",            Connection, XSD.xstring,
                "Aircraft type assigned to this connection.");
        prop(model, "maxPassengers",         Connection, XSD.integer,
                "Maximum number of passengers the assigned aircraft can carry.");

        // ---- Customer properties -------------------------------------------
        prop(model, "customerName",   Customer, XSD.xstring,
                "Full name of the customer.");
        prop(model, "passportNumber", Customer, XSD.xstring,
                "Passport number used as the customer's unique identifier.");

        // ---- Trip properties -----------------------------------------------
        objProp(model, "bookedBy",       Trip, Customer,
                "Links a trip to the customer who booked it. Answers query: who booked the trip?");
        prop(model, "bookingTimestamp",  Trip, XSD.dateTime,
                "Date and time at which the trip was booked.");
        prop(model, "tripPrice",         Trip, XSD.decimal,
                "Total ticket price for the trip in EUR.");
        prop(model, "tripStartTime",     Trip, XSD.dateTime,
                "Scheduled departure date-time of the first flight in this trip.");
        prop(model, "tripEndTime",       Trip, XSD.dateTime,
                "Scheduled arrival date-time of the last flight in this trip.");
        // hasFirstFlight is the entry point into the ordered flight sequence.
        // Together with nextFlight it forms a singly-linked list.
        objProp(model, "hasFirstFlight", Trip, Flight,
                "Entry point into the ordered flight sequence of this trip.");

        // ---- Flight properties ---------------------------------------------
        // departureAirport / arrivalAirport resolve the direction of travel on
        // the bidirectional Connection resource (design decision: separation of
        // connection metadata from directional booking data).
        objProp(model, "usesConnection",   Flight, Connection,
                "The scheduled connection this flight operates on.");
        objProp(model, "departureAirport", Flight, Airport,
                "Airport from which this flight departs. Together with arrivalAirport " +
                "gives direction to the bidirectional Connection.");
        objProp(model, "arrivalAirport",   Flight, Airport,
                "Airport at which this flight arrives.");
        prop(model, "seatNumber",          Flight, XSD.xstring,
                "Seat identifier assigned to the customer for this flight (e.g. '14A').");
        prop(model, "seatClass",           Flight, XSD.xstring,
                "Travel class booked for this seat: 'Economy' or 'Business'.");
        prop(model, "flightStartTime",     Flight, XSD.dateTime,
                "Scheduled departure date-time of this flight.");
        // flightEndTime enables computing elapsed time per flight against tripStartTime (query b).
        prop(model, "flightEndTime",       Flight, XSD.dateTime,
                "Scheduled arrival date-time of this flight.");
        // nextFlight makes the sequence constraint structurally explicit.
        objProp(model, "nextFlight",  Flight, Flight,
                "Links to the subsequent flight in the trip sequence (singly-linked list). " +
                "Encodes the 'connected in sequence' requirement directly in the graph.");
        // partOfTrip is the inverse of the hasFirstFlight / nextFlight chain;
        objProp(model, "partOfTrip",  Flight, Trip,
                "Back-link to the enclosing trip. Enables direct flight→trip lookup.");
    }

    // =========================================================================
    // AIRPORTS
    // =========================================================================

    /**
     * Creates the four airport instances with fictional but realistic values
     * for employee counts, managers, and operation start years.
     *
     * @return array [BER, JFK, FCO, LHR]
     */
    static Resource[] createAirports(Model model) {
        Resource BER = airport(model, "BER", "Willi Brandt Airport",
                "Berlin",   450, "Hans Mueller",  "1998");
        Resource JFK = airport(model, "JFK", "John F. Kennedy International Airport",
                "New York", 820, "John Smith",    "1995");
        Resource FCO = airport(model, "FCO", "Leonardo da Vinci International Airport",
                "Rome",     390, "Marco Rossi",   "2001");
        Resource LHR = airport(model, "LHR", "Heathrow Airport",
                "London",   650, "Emma Thompson", "1993");
        return new Resource[]{BER, JFK, FCO, LHR};
    }

    static Resource airport(Model model, String iata, String label, String city,
                             int employees, String manager, String yearStarted) {
        Resource a = model.createResource(NS + iata);
        model.add(a, RDF.type,                                          res(model, "Airport"));
        model.add(a, RDFS.label,                                        model.createLiteral(label));
        model.add(a, model.createProperty(NS + "iataCode"),             slit(model, iata));
        model.add(a, model.createProperty(NS + "locatedInCity"),        slit(model, city));
        model.add(a, model.createProperty(NS + "numberOfEmployees"),    model.createTypedLiteral(employees));
        model.add(a, model.createProperty(NS + "managerInCharge"),      slit(model, manager));
        model.add(a, model.createProperty(NS + "yearOfOperationsStart"),
                model.createTypedLiteral(yearStarted, XSDDatatype.XSDgYear));
        return a;
    }

    // =========================================================================
    // CONNECTIONS  (all C(4,2) = 6 unordered pairs)
    // =========================================================================

    /**
     * Creates one Connection resource per unordered airport pair.
     * Durations, aircraft, and capacities are fictional but plausible values.
     *
     * @return array [BER_JFK, BER_FCO, BER_LHR, JFK_FCO, JFK_LHR, FCO_LHR]
     */
    static Resource[] createConnections(Model model, Resource[] ap) {
        Resource BER = ap[0], JFK = ap[1], FCO = ap[2], LHR = ap[3];

        Resource cBER_JFK = conn(model, "Conn_BER_JFK", BER, JFK, 540, "Boeing 787 Dreamliner", 250);
        Resource cBER_FCO = conn(model, "Conn_BER_FCO", BER, FCO, 150, "Airbus A320",           180);
        Resource cBER_LHR = conn(model, "Conn_BER_LHR", BER, LHR, 120, "Airbus A319",           150);
        Resource cJFK_FCO = conn(model, "Conn_JFK_FCO", JFK, FCO, 540, "Boeing 777",            300);
        Resource cJFK_LHR = conn(model, "Conn_JFK_LHR", JFK, LHR, 420, "Boeing 747-400",        350);
        Resource cFCO_LHR = conn(model, "Conn_FCO_LHR", FCO, LHR, 150, "Airbus A321",           190);

        return new Resource[]{cBER_JFK, cBER_FCO, cBER_LHR, cJFK_FCO, cJFK_LHR, cFCO_LHR};
    }

    static Resource conn(Model model, String id,
                          Resource ap1, Resource ap2,
                          int durationMin, String plane, int maxPax) {
        Resource c = model.createResource(NS + id);
        model.add(c, RDF.type,   res(model, "Connection"));
        model.add(c, RDFS.label, model.createLiteral(ap1.getLocalName() + " - " + ap2.getLocalName()));
        model.add(c, model.createProperty(NS + "connectsAirport1"),       ap1);
        model.add(c, model.createProperty(NS + "connectsAirport2"),       ap2);
        model.add(c, model.createProperty(NS + "flightDurationMinutes"),  model.createTypedLiteral(durationMin));
        model.add(c, model.createProperty(NS + "planeModel"),             slit(model, plane));
        model.add(c, model.createProperty(NS + "maxPassengers"),          model.createTypedLiteral(maxPax));
        return c;
    }

    // =========================================================================
    // CUSTOMERS, TRIPS, AND FLIGHTS
    // =========================================================================

    /**
     * Creates three customers and their respective trips.
     *
     * All trips start on 01.05.2025 at 00:00 as required by the assignment.
     * Each trip has exactly four flights connected in sequence.
     *
     * Trip 1 (Timur Sultanov): BER -> FCO -> LHR -> JFK -> BER
     * Trip 2 (Tony Trinh):    LHR -> BER -> FCO -> JFK -> LHR
     * Trip 3 (Karl Napf):     JFK -> BER -> LHR -> FCO -> BER
     *
     * Query coverage:
     *   a) Trip 1 includes BER -> FCO (Berlin - Rome); query via departureAirport=BER,
     *      arrivalAirport=FCO, partOfTrip=airline:Trip1.
     *   b) Elapsed time per flight: subtract tripStartTime from each flightEndTime.
     *   c) Booking customer: Trip -> bookedBy -> Customer -> customerName.
     */
    static void createTrips(Model model, Resource[] ap, Resource[] conn) {
        Resource BER = ap[0], JFK = ap[1], FCO = ap[2], LHR = ap[3];
        Resource cBER_JFK = conn[0], cBER_FCO = conn[1], cBER_LHR = conn[2];
        Resource cJFK_FCO = conn[3], cJFK_LHR = conn[4], cFCO_LHR = conn[5];

        // ---- Trip 1: Timur Sultanov  –  BER -> FCO -> LHR -> JFK -> BER --------
        // This trip explicitly contains the Berlin-Rome (BER->FCO) leg (query a).
        Resource timur = customer(model, "Customer_TimurSultanov", "Timur Sultanov", "PP123456");

        Resource t1f1 = flight(model, "Trip1_Flight1", cBER_FCO, BER, FCO,
                "14A", "Economy",  "2025-05-01T00:00:00", "2025-05-01T02:30:00");
        Resource t1f2 = flight(model, "Trip1_Flight2", cFCO_LHR, FCO, LHR,
                "22B", "Economy",  "2025-05-01T05:00:00", "2025-05-01T07:30:00");
        Resource t1f3 = flight(model, "Trip1_Flight3", cJFK_LHR, LHR, JFK,
                "8C",  "Business", "2025-05-01T09:00:00", "2025-05-01T16:00:00");
        Resource t1f4 = flight(model, "Trip1_Flight4", cBER_JFK, JFK, BER,
                "31D", "Economy",  "2025-05-01T19:00:00", "2025-05-02T04:00:00");

        chain(model, t1f1, t1f2, t1f3, t1f4);

        Resource trip1 = trip(model, "Trip1", timur,
                "2025-04-15T10:00:00", "1250.00",
                "2025-05-01T00:00:00", "2025-05-02T04:00:00", t1f1);
        bindToTrip(model, trip1, t1f1, t1f2, t1f3, t1f4);

        // ---- Trip 2: Tony Trinh  –  LHR -> BER -> FCO -> JFK -> LHR --------
        Resource tony = customer(model, "Customer_TonyTrinh", "Tony Trinh", "PP789012");

        Resource t2f1 = flight(model, "Trip2_Flight1", cBER_LHR, LHR, BER,
                "5A",  "Business", "2025-05-01T00:00:00", "2025-05-01T02:00:00");
        Resource t2f2 = flight(model, "Trip2_Flight2", cBER_FCO, BER, FCO,
                "18C", "Economy",  "2025-05-01T04:00:00", "2025-05-01T06:30:00");
        Resource t2f3 = flight(model, "Trip2_Flight3", cJFK_FCO, FCO, JFK,
                "11F", "Business", "2025-05-01T09:00:00", "2025-05-01T18:00:00");
        Resource t2f4 = flight(model, "Trip2_Flight4", cJFK_LHR, JFK, LHR,
                "24E", "Economy",  "2025-05-01T20:00:00", "2025-05-02T03:00:00");

        chain(model, t2f1, t2f2, t2f3, t2f4);

        Resource trip2 = trip(model, "Trip2", tony,
                "2025-04-20T14:30:00", "980.50",
                "2025-05-01T00:00:00", "2025-05-02T03:00:00", t2f1);
        bindToTrip(model, trip2, t2f1, t2f2, t2f3, t2f4);

        // ---- Trip 3: Karl Napf  –  JFK -> BER -> LHR -> FCO -> BER -------
        Resource karl = customer(model, "Customer_KarlNapf", "Karl Napf", "PP345678");

        Resource t3f1 = flight(model, "Trip3_Flight1", cBER_JFK, JFK, BER,
                "33A", "Economy",  "2025-05-01T00:00:00", "2025-05-01T09:00:00");
        Resource t3f2 = flight(model, "Trip3_Flight2", cBER_LHR, BER, LHR,
                "7B",  "Business", "2025-05-01T11:00:00", "2025-05-01T13:00:00");
        Resource t3f3 = flight(model, "Trip3_Flight3", cFCO_LHR, LHR, FCO,
                "12D", "Economy",  "2025-05-01T15:00:00", "2025-05-01T17:30:00");
        Resource t3f4 = flight(model, "Trip3_Flight4", cBER_FCO, FCO, BER,
                "25C", "Economy",  "2025-05-01T20:00:00", "2025-05-01T22:30:00");

        chain(model, t3f1, t3f2, t3f3, t3f4);

        Resource trip3 = trip(model, "Trip3", karl,
                "2025-04-25T09:15:00", "750.00",
                "2025-05-01T00:00:00", "2025-05-01T22:30:00", t3f1);
        bindToTrip(model, trip3, t3f1, t3f2, t3f3, t3f4);
    }

    static Resource customer(Model model, String id, String name, String passport) {
        Resource c = model.createResource(NS + id);
        model.add(c, RDF.type,                                   res(model, "Customer"));
        model.add(c, RDFS.label,                                 model.createLiteral(name));
        model.add(c, model.createProperty(NS + "customerName"),  slit(model, name));
        model.add(c, model.createProperty(NS + "passportNumber"), slit(model, passport));
        return c;
    }

    static Resource trip(Model model, String id, Resource customer,
                          String bookingTs, String price,
                          String startTime, String endTime,
                          Resource firstFlight) {
        Resource t = model.createResource(NS + id);
        model.add(t, RDF.type,   res(model, "Trip"));
        model.add(t, RDFS.label, model.createLiteral(id));
        model.add(t, model.createProperty(NS + "bookedBy"),
                customer);
        model.add(t, model.createProperty(NS + "bookingTimestamp"),
                model.createTypedLiteral(bookingTs, XSDDatatype.XSDdateTime));
        model.add(t, model.createProperty(NS + "tripPrice"),
                model.createTypedLiteral(price, XSDDatatype.XSDdecimal));
        model.add(t, model.createProperty(NS + "tripStartTime"),
                model.createTypedLiteral(startTime, XSDDatatype.XSDdateTime));
        model.add(t, model.createProperty(NS + "tripEndTime"),
                model.createTypedLiteral(endTime, XSDDatatype.XSDdateTime));
        model.add(t, model.createProperty(NS + "hasFirstFlight"),
                firstFlight);
        return t;
    }

    static Resource flight(Model model, String id,
                            Resource connection, Resource from, Resource to,
                            String seat, String seatClass,
                            String startTime, String endTime) {
        Resource f = model.createResource(NS + id);
        model.add(f, RDF.type,   res(model, "Flight"));
        model.add(f, RDFS.label,
                model.createLiteral(from.getLocalName() + " to " + to.getLocalName()));
        model.add(f, model.createProperty(NS + "usesConnection"),    connection);
        model.add(f, model.createProperty(NS + "departureAirport"),  from);
        model.add(f, model.createProperty(NS + "arrivalAirport"),    to);
        model.add(f, model.createProperty(NS + "seatNumber"),        slit(model, seat));
        model.add(f, model.createProperty(NS + "seatClass"),         slit(model, seatClass));
        model.add(f, model.createProperty(NS + "flightStartTime"),
                model.createTypedLiteral(startTime, XSDDatatype.XSDdateTime));
        model.add(f, model.createProperty(NS + "flightEndTime"),
                model.createTypedLiteral(endTime, XSDDatatype.XSDdateTime));
        return f;
    }

    /** Links each flight to the next via airline:nextFlight. */
    static void chain(Model model, Resource... flights) {
        Property nextFlight = model.createProperty(NS + "nextFlight");
        for (int i = 0; i < flights.length - 1; i++) {
            model.add(flights[i], nextFlight, flights[i + 1]);
        }
    }

    /** Adds airline:partOfTrip to each flight as a back-link to its trip. */
    static void bindToTrip(Model model, Resource trip, Resource... flights) {
        Property partOfTrip = model.createProperty(NS + "partOfTrip");
        for (Resource f : flights) {
            model.add(f, partOfTrip, trip);
        }
    }

    // =========================================================================
    // SCHEMA HELPERS
    // =========================================================================

    /** Creates an rdfs:Class with label and comment. */
    static Resource cls(Model model, String localName, String comment) {
        Resource c = model.createResource(NS + localName, RDFS.Class);
        model.add(c, RDFS.label,   model.createLiteral(localName));
        model.add(c, RDFS.comment, model.createLiteral(comment));
        return c;
    }

    /**
     * Declares a datatype property (literal range) with domain, XSD range,
     * rdfs:label, and rdfs:comment.
     */
    static Property prop(Model model, String localName, Resource domain,
                          Resource rangeXsd, String comment) {
        Property p = model.createProperty(NS + localName);
        model.add(p, RDF.type,     RDF.Property);
        model.add(p, RDFS.label,   model.createLiteral(localName));
        model.add(p, RDFS.domain,  domain);
        model.add(p, RDFS.range,   rangeXsd);
        model.add(p, RDFS.comment, model.createLiteral(comment));
        return p;
    }

    /**
     * Declares an object property (resource range) with domain, class range,
     * rdfs:label, and rdfs:comment.
     */
    static Property objProp(Model model, String localName,
                             Resource domain, Resource range, String comment) {
        Property p = model.createProperty(NS + localName);
        model.add(p, RDF.type,     RDF.Property);
        model.add(p, RDFS.label,   model.createLiteral(localName));
        model.add(p, RDFS.domain,  domain);
        model.add(p, RDFS.range,   range);
        model.add(p, RDFS.comment, model.createLiteral(comment));
        return p;
    }

    /** Returns (or creates) a resource in the ontology namespace. */
    static Resource res(Model model, String localName) {
        return model.createResource(NS + localName);
    }

    /** Creates an xsd:string typed literal. */
    static Literal slit(Model model, String value) {
        return model.createTypedLiteral(value, XSDDatatype.XSDstring);
    }
}
