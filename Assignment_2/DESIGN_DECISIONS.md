# Exercise 3 — Querying DBLP with SPARQL: Queries and Design Decisions

**Endpoint:** <https://sparql.dblp.org/> (DBLP's public service, powered by the *QLever* engine, full SPARQL 1.1).
**Schema namespace:** `dblp: <https://dblp.org/rdf/schema#>`
**Recorded:** 2026-06-02. DBLP is rebuilt monthly, so the absolute numbers below can drift without making the queries wrong (as the assignment notes).

The lecture and the course textbooks introduce **SPARQL 1.0**; several tasks (aggregation, `GROUP BY`, `HAVING`, `SAMPLE`, `COALESCE`, `VALUES`) require **SPARQL 1.1** features, which are taken from the W3C Recommendation. References are abbreviated as:

- **FOST** = Hitzler, Krötzsch & Rudolph (2009), *Foundations of Semantic Web Technologies*, Chapman & Hall/CRC — chapter on RDF query languages / SPARQL.
- **SW** = Hitzler, Krötzsch, Rudolph & Sure (2008), *Semantic Web — Grundlagen*, eXamen.press — SPARQL chapter.
- **SPARQL 1.1** = *SPARQL 1.1 Query Language*, W3C Recommendation, 21 March 2013, <https://www.w3.org/TR/sparql11-query/>.
- **SPARQL 1.0** = *SPARQL Query Language for RDF*, W3C Recommendation 2008, <https://www.w3.org/TR/rdf-sparql-query/>.

---

## 0. How the schema was determined (and why that matters)

Rather than guess vocabulary, the schema was introspected directly on the endpoint:

- `SELECT DISTINCT ?p WHERE { ?s ?p ?o }` → all predicates actually used.
- `SELECT DISTINCT ?c WHERE { ?s a ?c }` → all classes actually used.

The terms used below are therefore the ones that genuinely occur in the data. The relevant ones:

| Purpose | Term | Notes (verified) |
|---|---|---|
| Publication super-class | `dblp:Publication` | Every record carries this type **and** a specific sub-type (`dblp:Article`, `dblp:Inproceedings`, …). |
| Person | `dblp:Person` | Disambiguated person record; sub-class of `dblp:Creator`. |
| Authorship (direct) | `dblp:authoredBy` | `Publication → Creator`. |
| Author display name | `dblp:primaryCreatorName` | Canonical name; variants live in `dblp:creatorName`. |
| Title | `dblp:title` | xsd:string. |
| Venue label | `dblp:publishedIn` | Literal name of journal/book/proceedings, e.g. *"…Volume 1 (1)"*. |
| Year | `dblp:yearOfPublication` | **`xsd:gYear`** — see §G. |
| Author count | `dblp:numberOfCreators` | `xsd:int`. |
| ORCID | `dblp:orcid` | An **IRI** `https://orcid.org/…`, not a bare string. |
| Wikipedia | `dblp:wikipedia` | Present on ~9.9k creators. |
| Affiliation | `dblp:affiliation` | Literal, e.g. *"University of Trier … Germany"*. |
| Authorship (reified) | `dblp:hasSignature` → `dblp:AuthorSignature` | Carries `dblp:signatureCreator`, `dblp:signatureOrdinal` (**`xsd:int`, 1-based**), `dblp:signaturePublication`, `dblp:signatureDblpName`. |

Resolved identifiers used throughout:

- Ralph Bergmann → `https://dblp.org/pid/b/RalphBergmann`
- Alexander Schultheis → `https://dblp.org/pid/321/8225`
- Ingo J. Timm → `https://dblp.org/pid/60/2246`

Using these **PIDs** instead of matching a name string is a deliberate decision: names are ambiguous and exist in several spelling variants, whereas the PID identifies exactly one person (FOST: identity of resources via IRIs).

---

## a) Number of triples

```sparql
SELECT (COUNT(*) AS ?tripleCount)
WHERE { ?s ?p ?o }
```

**Design.** "The ontology" here means the whole loaded DBLP graph. The most general triple pattern `?s ?p ?o` matches every statement (the *Basic Graph Pattern* of FOST / SPARQL 1.0 §2); wrapping it in `COUNT(*)` counts solutions, i.e. triples.
**1.1 feature:** aggregate `COUNT` (SPARQL 1.1 §11 *Aggregates*). `COUNT(*)` counts rows rather than a particular bound variable.
**Result (2026-06-02):** **1 574 328 480** triples.

---

## b) Number of publications

```sparql
SELECT (COUNT(?publication) AS ?publicationCount)
WHERE { ?publication a dblp:Publication }
```

**Design.** I verified that DBLP materialises the super-type on *every* record (`dblp:Publication` = 8 554 462; the single largest sub-type `dblp:Article` = 3 357 537). Because the super-type is asserted explicitly, one type triple is enough — no `UNION` over sub-classes and **no reliance on RDFS subclass inference** (QLever answers the raw graph; FOST stresses that without an entailment regime you only get asserted triples). The keyword `a` abbreviates `rdf:type` (SPARQL/Turtle syntax abbreviation).
**Result (2026-06-02):** **8 554 462** publications.

---

## c) Author with ORCID 0000-0002-5515-7158

```sparql
SELECT ?author ?name
WHERE {
  ?author dblp:orcid             <https://orcid.org/0000-0002-5515-7158> ;
          dblp:primaryCreatorName ?name .
}
```

**Design.** Introspection showed ORCIDs are stored as **IRIs** in the `orcid.org` namespace, not as plain literals — so the object is written as an IRI, not `"0000-…"`. (A first attempt with a string literal returned nothing; this is exactly the literal-vs-IRI term-matching point of FOST.) The `;` is predicate-object list syntax sharing the subject `?author`.
**Result:** `https://dblp.org/pid/b/RalphBergmann` → **"Ralph Bergmann"** (the ORCID belongs to the course's own Prof. Bergmann).

---

## d) Authors with a Wikipedia page

```sparql
SELECT (COUNT(DISTINCT ?author) AS ?authorsWithWikipedia)
WHERE { ?author dblp:wikipedia ?wikipediaPage }
```

**Design.** `DISTINCT` inside the aggregate is essential: a person may have several `dblp:wikipedia` links (e.g. different language versions), and the question asks for *authors*, not links — so each author must be counted once (SPARQL 1.1 §11, aggregate with `DISTINCT`). `dblp:wikipedia` is only asserted on creators, so an extra `?author a dblp:Person` filter is optional and does not change the intent.
**Result (2026-06-02):** **9 899** authors.

---

## e) Papers whose title contains "CBR"

```sparql
SELECT ?paper ?title
WHERE {
  ?paper dblp:title ?title .
  FILTER (CONTAINS(?title, "CBR"))
}
LIMIT 10
```

**Design.** `CONTAINS` (SPARQL 1.1 §17.4.3 *String functions*) tests a substring and is **case-sensitive**, matching the upper-case acronym "CBR". `LIMIT` (SPARQL 1.1 §15 *Solution Sequences and Modifiers*; FOST "result-form modifiers") caps the output at ten.
**Caveat documented on purpose:** a substring test also matches super-strings such as "**CBR**S" or "**CBR**OKER". If only the standalone token is wanted, use a word-boundary regex instead: `FILTER(REGEX(?title, "\\bCBR\\b"))`. The literal reading of the task ("contain CBR") justifies `CONTAINS`.
**Result (2026-06-02):** 10 rows, e.g. *"Flexible Querying Techniques Based on CBR."*, *"Reasoning as Remembering: The Theory and Practice of CBR."*, *"Explanation Support for the Case-Based Reasoning Tool myCBR."*

---

## f) Venues of the co-authors on the joint Bergmann–Schultheis papers

```sparql
SELECT ?venue (COUNT(DISTINCT ?coauthorPaper) AS ?numPublications)
WHERE {
  ?jointPaper dblp:authoredBy <https://dblp.org/pid/b/RalphBergmann> ;
              dblp:authoredBy <https://dblp.org/pid/321/8225> ;
              dblp:authoredBy ?coauthor .
  FILTER (?coauthor != <https://dblp.org/pid/b/RalphBergmann> &&
          ?coauthor != <https://dblp.org/pid/321/8225>)
  ?coauthorPaper dblp:authoredBy  ?coauthor ;
                 dblp:publishedIn ?venue .
}
GROUP BY ?venue
ORDER BY DESC(?numPublications)
LIMIT 10
```

**Design — read the task as three steps:**

1. **Joint papers.** Repeating `dblp:authoredBy` twice on the same `?jointPaper` (once per PID) is a conjunctive Basic Graph Pattern, i.e. set **intersection** — the paper must have *both* people as authors (FOST: BGP matching).
2. **The other co-authors.** A third `dblp:authoredBy ?coauthor` enumerates that paper's authors; the `FILTER … != … && … !=` removes the two named researchers, leaving the genuine co-authors.
3. **Their venues.** A *second* triple group takes **all** publications of those co-authors and reads `dblp:publishedIn` (the venue label for books/proceedings/journals). `GROUP BY ?venue` with `COUNT(DISTINCT ?coauthorPaper)` gives "number of publications per venue"; `DISTINCT` stops a paper being double-counted when it has several of these co-authors. `ORDER BY DESC(...) LIMIT 10` returns the top ten.

**1.1 features:** `GROUP BY`, aggregate `COUNT(DISTINCT …)` (§11); `ORDER BY` + `LIMIT` (§15 *Solution Sequences and Modifiers*). `publishedIn` was chosen over the structural `dblp:publishedAsPartOf` because the task asks for the **name** of the book/proceedings, which is exactly the `publishedIn` literal.

---

## g) Publications since 2020 by Bergmann or Timm (DFKI Trier)

```sparql
PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
SELECT (COUNT(DISTINCT ?publication) AS ?numPublications)
WHERE {
  VALUES ?author {
    <https://dblp.org/pid/b/RalphBergmann>
    <https://dblp.org/pid/60/2246>
  }
  ?publication dblp:authoredBy        ?author ;
               dblp:yearOfPublication ?year .
  FILTER (xsd:integer(STR(?year)) >= 2020)
}
```

**Design.**
- **"Bergmann *or* Timm":** `VALUES` supplies the two PIDs as inline data (SPARQL 1.1 §10.2). Equivalent to `FILTER(?author IN (…))` or a `UNION`, but `VALUES` is the most readable.
- **`COUNT(DISTINCT …)`** so a paper co-authored by *both* is counted once.
- **The gYear trap (§G):** `dblp:yearOfPublication` is typed `xsd:gYear`. The naïve `FILTER(?year >= 2020)` was tested and returns **0** — a `gYear` and an `xsd:integer` are not comparable, so every row is silently dropped. `STR(?year)` yields the plain string `"2020"`, and `xsd:integer( … )` turns it into a number that can be compared. ("since … 2020" is read inclusively, hence `>= 2020`.)

**1.1 features:** `VALUES` (§10.2), constructor `xsd:integer` (§17.5 *Constructor functions*), `STR` (§17.4.2).

---

## h) German researchers with ≥ 800 publications

```sparql
SELECT ?researcher ?name
       (SAMPLE(?aff) AS ?affiliation)
       (COUNT(DISTINCT ?publication) AS ?numPublications)
WHERE {
  ?researcher a                       dblp:Person ;
              dblp:primaryCreatorName ?name ;
              dblp:affiliation        ?aff .
  FILTER (CONTAINS(?aff, "Germany"))
  ?publication dblp:authoredBy ?researcher .
}
GROUP BY ?researcher ?name
HAVING (COUNT(DISTINCT ?publication) >= 800)
ORDER BY DESC(?numPublications)
LIMIT 10
```

**Design — the "count each person once" requirement drives every choice:**

- **Group by the *URI*, not the name.** Each human is one `dblp:Person` URI; the spelling variants are `dblp:creatorName` literals hanging off that single URI. Grouping by `?researcher` (the PID) therefore already collapses all name variants into one row. `?name` (the canonical `primaryCreatorName`) is functionally dependent on the URI, so adding it to `GROUP BY` is safe and lets us project it.
- **`COUNT(DISTINCT ?publication)`** guarantees publications are counted once even though joining a person who has several German affiliation strings multiplies intermediate rows.
- **`a dblp:Person`** excludes `dblp:AmbiguousCreator` / group pages, so homonym buckets don't masquerade as one prolific person.
- **Affiliation display:** because a person can hold several affiliations, affiliation is **not** in `GROUP BY` (that would split the person again). `SAMPLE(?aff)` returns one value, and since `?aff` is filtered to contain "Germany", the sampled value is a German one.
- **`HAVING`** filters *after* aggregation (the ≥ 800 threshold cannot be expressed with `FILTER`, which runs before grouping). `ORDER BY DESC` + `LIMIT 10` give the ten largest.

**1.1 features:** `GROUP BY`, `HAVING`, `COUNT(DISTINCT)`, `SAMPLE` (all §11); `CONTAINS` (§17.4.3).

---

## i) Bergmann's co-authors ranked by first-authored publications

```sparql
SELECT ?coauthor ?name
       (COALESCE(SAMPLE(?aff),     "no affiliation") AS ?affiliation)
       (COALESCE(SAMPLE(?orcidId), "unknown")        AS ?orcid)
       (COUNT(DISTINCT ?firstAuthoredPaper) AS ?numFirstAuthored)
WHERE {
  ?sharedPaper dblp:authoredBy <https://dblp.org/pid/b/RalphBergmann> ;
               dblp:authoredBy ?coauthor .
  FILTER (?coauthor != <https://dblp.org/pid/b/RalphBergmann>)
  ?coauthor dblp:primaryCreatorName ?name .

  ?firstAuthoredPaper dblp:hasSignature ?sig .
  ?sig dblp:signatureCreator ?coauthor ;
       dblp:signatureOrdinal ?ordinal .
  FILTER (?ordinal = 1)

  OPTIONAL { ?coauthor dblp:affiliation ?aff }
  OPTIONAL { ?coauthor dblp:orcid       ?orcidId }
}
GROUP BY ?coauthor ?name
ORDER BY DESC(?numFirstAuthored)
LIMIT 12
```

**Design.**
- **Co-author set:** two `dblp:authoredBy` on the same `?sharedPaper`, minus Bergmann himself (`FILTER … !=`).
- **"First author":** authorship order lives on the **reified signature**. A publication's `dblp:hasSignature` whose `dblp:signatureOrdinal` is `1` marks its first author. *Counted across all of DBLP* (a co-author's overall first-author output), which is how "have the most publications as first author" reads. `COUNT(DISTINCT ?firstAuthoredPaper)` is the ranking key.
- **`signatureOrdinal` term-matching pitfall (§G):** the property is typed `xsd:int`. A bare `1` in a triple pattern is an `xsd:integer` and would **not** term-match the stored `xsd:int` value. Writing it as the *value* test `FILTER(?ordinal = 1)` compares numerically across the integer types and matches (verified: 8 488 750 first-author signatures). `"1"^^xsd:int` in the pattern would work too; the `FILTER` form is the most robust.
- **"Still include authors without affiliation":** `dblp:affiliation` is wrapped in `OPTIONAL`, so a missing affiliation does not drop the author (SPARQL 1.0 §6 / FOST "optional patterns"). `COALESCE(SAMPLE(?aff), "no affiliation")` prints a placeholder.
- **ORCID "unknown" if absent:** `OPTIONAL` + `SAMPLE` + `COALESCE(…, "unknown")`. `SAMPLE` collapses the (per the task, identical) ORCID to a single value; `COALESCE` (§17.4.2) supplies the fallback when none exists. The person-level `dblp:orcid` already consolidates the per-paper `dblp:signatureOrcid`, matching "as soon as it is listed once … assume it is always the same".

**1.1 features:** `GROUP BY`, `SAMPLE`, `COUNT(DISTINCT)` (§11); `COALESCE` (§17.4.2); `OPTIONAL` (§6).

---

## j) Most recent Bergmann publication

```sparql
SELECT ?publication ?title ?firstAuthorName ?numAuthors ?year
WHERE {
  ?publication dblp:authoredBy        <https://dblp.org/pid/b/RalphBergmann> ;
               dblp:title             ?title ;
               dblp:yearOfPublication ?year ;
               dblp:numberOfCreators  ?numAuthors ;
               dblp:hasSignature      ?sig .
  ?sig dblp:signatureOrdinal ?ordinal ;
       dblp:signatureCreator ?firstAuthor .
  FILTER (?ordinal = 1)
  ?firstAuthor dblp:primaryCreatorName ?firstAuthorName .
}
ORDER BY DESC(?year)
LIMIT 1
```

**Design.**
- **"Most recent":** `ORDER BY DESC(?year)` + `LIMIT 1`. Note the contrast with task (g): here the comparison is `gYear` **against `gYear`**, which *is* well defined, so no cast is needed for ordering — only cross-type (`gYear` vs `integer`) comparison fails.
- **Required columns:** `dblp:title` (name), `dblp:numberOfCreators` (number of authors), `dblp:yearOfPublication` (year), and the first author resolved through the ordinal-1 signature → `dblp:signatureCreator` → `dblp:primaryCreatorName`.
- **Known limitation, stated for honesty:** if several papers share the latest year, `LIMIT 1` returns an arbitrary one of them. A finer order (e.g. additionally by `dblp:monthOfPublication`, where present) or returning all rows of the maximum year would resolve ties.

**1.1 features used elsewhere; here mainly §15 modifiers (`ORDER BY`, `LIMIT`).**

---

## G. Cross-cutting design decisions (the subtle bits)

1. **`xsd:gYear` is not an integer.** `dblp:yearOfPublication` is `xsd:gYear`. `FILTER(?year >= 2020)` silently yields **0 results** (verified) because SPARQL's relational operators are only defined within compatible datatypes (SPARQL 1.1 §17.3 *Operator mapping*). Range filters therefore cast: `xsd:integer(STR(?year))`. *Ordering* among `gYear` values (task j) needs no cast, since both sides share the datatype.
2. **`xsd:int` ordinals and term vs. value matching.** `dblp:signatureOrdinal` is `xsd:int`. RDF triple matching is by *exact term*, so a plain `1` (`xsd:integer`) would not match. Using `FILTER(?ordinal = 1)` switches to *value* comparison, which holds across the numeric types (FOST distinguishes term identity from value equality).
3. **`DISTINCT` in every count.** Multi-valued properties (`wikipedia`, `affiliation`) and many-to-many joins (co-authors, venues) inflate intermediate rows; `COUNT(DISTINCT …)` keeps "count the things, not the join rows".
4. **PIDs over name strings.** All person references use the resolved DBLP PID, removing name-ambiguity (FOST: IRIs as global identifiers).
5. **No inference assumed.** The endpoint evaluates the asserted graph (no RDFS/OWL entailment), so queries rely on explicitly stored triples — which is why counting `dblp:Publication` directly (b) is valid only because DBLP materialises that type.

---

## How the results were obtained

Tasks **a–e** were executed against the live endpoint and their results/date are given above. Tasks **f–j** were validated piece-by-piece (vocabulary, datatypes, author PIDs, the signature/ordinal mechanism, the `gYear`/`xsd:int` behaviour) against the live endpoint; the full queries should be run in the QLever web UI at <https://sparql.dblp.org/> to capture the result screenshots required by the submission (paste each query from `queries_E3.rq`, then record the date next to the screenshot as the assignment specifies).

## References

- **SPARQL 1.1 Query Language**, W3C Recommendation, 21 Mar 2013. §6 Optional, §10.2 VALUES, §11 Aggregates (`COUNT`, `SAMPLE`, `GROUP BY`, `HAVING`), §15 Solution Sequences and Modifiers (`ORDER BY`, `DISTINCT`, `LIMIT`), §17 Expressions (`CONTAINS`, `STR`, `COALESCE`, `IN`, constructor `xsd:integer`). <https://www.w3.org/TR/sparql11-query/>
- **SPARQL Query Language for RDF**, W3C Recommendation, 2008 (SPARQL 1.0 — BGPs, `FILTER`, `OPTIONAL`, solution modifiers). <https://www.w3.org/TR/rdf-sparql-query/>
- **Hitzler, Krötzsch & Rudolph (2009).** *Foundations of Semantic Web Technologies.* Chapman & Hall/CRC — RDF query languages / SPARQL chapter (BGP matching, term vs. value, optional patterns, result modifiers).
- **Hitzler, Krötzsch, Rudolph & Sure (2008).** *Semantic Web — Grundlagen.* eXamen.press — SPARQL chapter.
- **DBLP RDF schema / documentation:** <https://dblp.org/rdf/> and the SPARQL service announcement <https://blog.dblp.org/2024/09/09/introducing-our-public-sparql-query-service/>.
- **QLever** (endpoint engine): <https://github.com/ad-freiburg/qlever>.
