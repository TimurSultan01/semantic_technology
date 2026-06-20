# Aufgabe 4 – Sandwich-Ontologie: Begründung der Modellierungs-Entscheidungen

Portfolio-Aufgabe 2, Exercise 4 (Familiarisierung mit Protégé) · Semantische Technologien, Universität Trier

Die Datei **`sandwich.owl`** ist in RDF/XML serialisiert und enthält zu jeder Entscheidung eine `rdfs:comment`-Annotation. Jede Design-Entscheidung folgt den Konventionen des **Manchester OWL-Tutorials** (Begleitbeispiel `pizza.owl`).

---

## 1. Grundstruktur: Sandwich vs. SandwichIngredient

Wie im Pizza-Tutorial (`Pizza` vs. `PizzaTopping`) bilden zwei **disjunkte** Oberklassen das Gerüst:

- `Sandwich` – die Sandwiches selbst
- `SandwichIngredient` – die Zutaten

`Sandwich owl:disjointWith SandwichIngredient` stellt sicher, dass nichts gleichzeitig Sandwich und Zutat ist (Tutorial-Folie „Disjoints“).

## 2. Disjunktes primitives Klassengerüst (Zutaten)

`SandwichIngredient` wird in die sechs geforderten, **paarweise disjunkten** Kategorien geteilt: `Bread`, `Sauce`, `Vegetable`, `Fish`, `Cheese`, `Meat` (Tutorial: „disjoint tree of primitive classes“ / „Primitive Skeleton“). Umgesetzt mit `owl:AllDisjointClasses`.

Die konkreten Zutaten sind – analog zu den Topping-Klassen der Pizza-Ontologie – **Unterklassen**, nicht Individuen:

| Kategorie | Sorten (Unterklassen) |
|-----------|------------------------|
| Bread | Ciabatta, Baguette, WhiteBread, WholeWheatBread |
| Sauce | Ketchup, Mayonnaise, TomatoSauce |
| Vegetable | Cucumber, GreenSalad, Avocado, Basil |
| Fish | Salmon, Tuna |
| Cheese | Mozzarella |
| Meat | Prosciutto, Salami |

Die Aufgabe verlangt „mindestens zwei Sorten Brot“ – modelliert sind vier. Auf jeder Ebene sind die Geschwisterklassen wieder disjunkt (Tutorial: „Add all siblings as disjoint at each level“).

## 3. Rollen hasIngredient / isIngredientOf

- `hasIngredient` (Object Property): **Domain** `Sandwich`, **Range** `SandwichIngredient`.
- `isIngredientOf`: über `owl:inverseOf` als **inverse Rolle** zu `hasIngredient` deklariert.

### Charakteristika von hasIngredient (mit Begründung als Annotation)

| Merkmal | Gewählt? | Begründung |
|---------|:---:|------------|
| Funktional | nein | Ein Sandwich hat mehrere Zutaten. |
| Invers-funktional | nein | Dieselbe Zutat (z. B. `baguette`, `ciabatta`) wird von mehreren Sandwiches verwendet. *(Anders als Pizza-`hasTopping`, das invers-funktional ist.)* |
| Transitiv | nein | Zutaten sind atomar; zudem erfordern qualifizierte Kardinalitäten und Asymmetrie/Irreflexivität eine **einfache** Eigenschaft. |
| Symmetrisch | nein | Zutat „hat“ nicht das Sandwich (Klassen sind disjunkt). |
| **Asymmetrisch** | **ja** | aus `x hasIngredient y` folgt `nicht y hasIngredient x`. |
| Reflexiv | nein | Ein Sandwich ist nicht seine eigene Zutat. |
| **Irreflexiv** | **ja** | Nichts ist Zutat seiner selbst (folgt aus Asymmetrie, zusätzlich angegeben). |

## 4. Restriktionen auf Sandwich (notwendige Bedingungen)

„Jedes Sandwich hat genau zwei Brote und mindestens zwei weitere Zutaten, davon mindestens eine Sauce“ → drei `rdfs:subClassOf`-Restriktionen auf `Sandwich`:

- `hasIngredient` **exactly 2** `Bread`  (qualifizierte Kardinalität)
- `hasIngredient` **some** `Sauce`  (Existenz-Restriktion, Tutorial-Stil)
- `hasIngredient` **min 2** (`SandwichIngredient` and not `Bread`)  (mind. zwei Nicht-Brot-Zutaten)

## 5. Definierte Kategorie-Klassen + Reasoner (das Herzstück des Tutorials)

Die drei Sandwich-Kategorien sind **definierte Klassen** (notwendig **und** hinreichend), damit der Reasoner – wie im Tutorial („let the reasoner do it!“) – die Polyhierarchie ableitet:

- `SandwichWithoutMeat ≡ Sandwich and (hasIngredient only (not Meat))`
- `SandwichWithoutFish ≡ Sandwich and (hasIngredient only (not Fish))`
- `SandwichOnlyVegetables ≡ Sandwich and (hasIngredient only (Bread or Sauce or Vegetable))`

Weil `Bread`, `Sauce`, `Vegetable` disjunkt zu `Meat` und `Fish` sind, **schlussfolgert der Reasoner automatisch**:

> `SandwichOnlyVegetables ⊑ SandwichWithoutMeat` **und** `⊑ SandwichWithoutFish`

Damit ist die Anforderung „Sandwiches nur mit Gemüse gehören auch zu ohne-Fleisch und ohne-Fisch“ erfüllt – **ohne** manuelle Mehrfach-Vererbung (Tutorial: „Untangling / Defined Classes“). Diese Kategorie-Klassen sind **nicht** untereinander disjunkt.

## 6. Individuen

Drei Sandwich-Individuen mit ihren Zutaten (wiederverwendete Zutaten-Individuen):

| Individuum | Zutaten | Asserted Typ(en) |
|------------|---------|------------------|
| ItalianSandwich | Ciabatta, Baguette, TomatoSauce, Basil, Mozzarella | WithoutFish, WithoutMeat |
| ProsciuttoSandwich | WhiteBread, Baguette, Ketchup, Prosciutto, Cucumber | WithoutFish |
| SalmonSandwich | WholeWheatBread, Ciabatta, Mayonnaise, Salmon, Avocado | WithoutMeat |

Ein `owl:AllDifferent`-Axiom (Eindeutige-Namen-Annahme) sorgt dafür, dass die Kardinalitäten „genau 2 Bread“ / „min 2 weitere“ korrekt ausgewertet werden.

## 7. Reasoner-Prüfung (vorab durchgeführt)

Mit HermiT verifiziert:

- **Ontologie ist konsistent**, keine inkonsistenten Klassen.
- `SandwichOnlyVegetables` wird als Unterklasse von `SandwichWithoutMeat` **und** `SandwichWithoutFish` **abgeleitet** (blaue Kanten im inferred view).
- Instanz-Klassifikation ist sinnvoll: ItalianSandwich → ohne Fleisch + ohne Fisch; ProsciuttoSandwich → nur ohne Fisch (enthält Fleisch); SalmonSandwich → nur ohne Fleisch (enthält Fisch).
- Gegenprobe: Ein fleischhaltiges Sandwich in `SandwichWithoutMeat` zu zwingen, erzeugt korrekt eine **Inkonsistenz** – die `only (not Meat)`-Restriktion greift also wirklich.

## 8. Was noch in Protégé zu tun ist

1. `sandwich.owl` in Protégé öffnen (File → Open).
2. **Reasoner starten** (Reasoner → Start reasoner, z. B. HermiT) und den *inferred view* prüfen.
3. **OntoGraf** öffnen (Window → Tabs → OntoGraf) und einen Screenshot des Graphen erstellen.
4. `sandwich.owl` (RDF/XML) + OntoGraf-Screenshot im Ordner **E4** der Abgabe-ZIP ablegen.

> Der OntoGraf-Screenshot lässt sich nur in Protégé selbst erzeugen; die beiliegende SVG-Grafik `E4_Struktur.svg` dient als Vorschau/Orientierung.
