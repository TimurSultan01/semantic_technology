package org.semtec.one.one;
import org.apache.jena.rdf.model.*;
import org.apache.jena.vocabulary.*;

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
public class Main {
    public static void main(String[] args) {
        Model model = ModelFactory.createDefaultModel();

        // Namespaces
        String base   = "http://uni-trier.de/wi2/";
        String schema = "http://schema.org/";

        // URIs
        Resource professur = model.createResource(
                "https://www.uni-trier.de/universitaet/fachbereiche-faecher/" +
                        "fachbereich-iv/faecher/informatikwissenschaften/professuren/" +
                        "wirtschaftsinformatik-2/professur");

        Resource ralph    = model.createResource(base + "RalphBergmann");
        Resource lecture  = model.createResource(base + "SemanticTechnologies");
        Resource seminar  = model.createResource(base + "ProjectSeminarST");
        Resource alex     = model.createResource(base + "AlexanderSchultheis");

        // Properties
        Property creator    = model.createProperty(schema, "creator");
        Property name       = model.createProperty(schema, "name");
        Property email      = model.createProperty(schema, "email");
        Property teaches    = model.createProperty(schema, "teaches");
        Property hasPart    = model.createProperty(schema, "hasPart");
        Property numAssign  = model.createProperty(base,   "numberOfAssignments");

        // Statements: Ralph Bergmann
        model.add(professur, creator,   ralph);
        model.add(ralph,     name,      "Ralph Bergmann");
        model.add(ralph,     email,     "bergmann@uni-trier.de");

        // Statements: lecture
        model.add(ralph,    teaches,   lecture);
        model.add(lecture,  name,      "Semantic Technologies");
        model.add(lecture,  hasPart,   seminar);

        // Statements: seminar
        model.add(seminar,  name,      "Project Seminar Semantic Technologies");
        model.add(seminar,  numAssign, model.createTypedLiteral(3));
        model.add(alex,     teaches,   seminar);

        // Statements: Alexander Schultheis
        model.add(alex, name,  "Alexander Schultheis");
        model.add(alex, email, "Alexander.Schultheis@uni-trier.de");

        // Serialize to RDF/XML
        model.write(System.out, "RDF/XML-ABBREV");
    }
}