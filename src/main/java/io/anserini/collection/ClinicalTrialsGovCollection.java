/*
 * Anserini: A Lucene toolkit for reproducible information retrieval research
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.anserini.collection;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.validation.SchemaFactory;
import java.io.*;
import java.net.URL;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * An instance of the ClinicalTrials.Gov Corpus.
 * This class works for both compressed <code>zip</code> files or uncompressed <code>xml</code>
 * files.
 */
public class ClinicalTrialsGovCollection extends DocumentCollection<ClinicalTrialsGovCollection.Document> {
  private static final Logger LOG = LogManager.getLogger(ClinicalTrialsGovCollection.class);

  public ClinicalTrialsGovCollection(Path path) {
    this.path = path;
    this.allowedFileSuffix = new HashSet<>(Arrays.asList(".xml", ".zip"));
  }

  @Override
  public FileSegment<ClinicalTrialsGovCollection.Document> createFileSegment(Path p) throws IOException {
    return new Segment(p);
  }

  /**
   * An individual file from the
   * ClinicalTrials.Gov Corpus.
   * This class works for both compressed <code>zip</code> files or uncompressed <code>xml</code>
   * files.
   */
  public static class Segment extends FileSegment<ClinicalTrialsGovCollection.Document>{
    private final ClinicalTrialsGovCollection.Parser parser = new ClinicalTrialsGovCollection.Parser();
    private ZipInputStream zipInput = null;
    private ZipEntry nextEntry = null;

    public Segment(Path path) throws IOException {
      super(path);
      if (this.path.toString().endsWith(".zip")) {
        zipInput = new ZipInputStream(new FileInputStream(path.toFile()));
      }
    }

    @Override
    protected void readNext() throws IOException, NoSuchElementException {
      try {
        if (path.toString().endsWith(".zip")) {
          getNextEntry();
          bufferedReader = new BufferedReader(new InputStreamReader(zipInput, "UTF-8"));
          File file = new File(nextEntry.getName()); // this is actually not a real file, only to match the method in Parser
          bufferedRecord = parser.parseFile(bufferedReader, file);
        } else {
          bufferedReader = new BufferedReader(new InputStreamReader(new FileInputStream(path.toFile()), "UTF-8"));
          bufferedRecord = parser.parseFile(bufferedReader, path.toFile());
          atEOF = true; // if it is a xml file, the segment only has one file, boolean to keep track if it's been read.
        }
      } catch (IOException e1) {
        if (path.toString().endsWith(".xml")) {
          atEOF = true;
        }
        throw e1;
      }
    }

    private void getNextEntry() throws IOException {
      nextEntry = zipInput.getNextEntry();
      if (nextEntry == null) {
        throw new NoSuchElementException();
      }
      // an ArchiveEntry may be a directory, so we need to read a next one.
      //   this must be done after the null check.
      if (nextEntry.isDirectory() || (!nextEntry.getName().endsWith(".xml"))) {
        getNextEntry();
      }
    }
  }

  /**
   * A document from the ClinicalTrials.Gov Corpus
   */
  public static class Document implements SourceDocument {
    private final RawDocument raw;
    private String id;
    private String contents;

    // No public constructor; must use parser to create document.
    private Document(RawDocument raw) {
      this.raw = raw;
    }

    @Override
    public String id() {
      return id;
    }

    @Override
    public String contents() {
      return contents;
    }

    @Override
    public String raw() {
      return contents;
    }

    @Override
    public boolean indexable() {
      return true;
    }

    public RawDocument getRawDocument() {
      return raw;
    }
  }

  // We intentionally segregate the Anserini ClinicalTrialsGovCollectionDocument from the parsed document below.

  /**
   * Raw container class for a document from a ClinicalTrials.gov Corpus.
   */
  public static class RawDocument {
    /** The file from which this object was read. */
    protected File sourceFile;

    /**
     * The org_study_id field specifies a code that is guaranteed to be unique for
     * every document in the corpus.
     */
    protected String orgStudyId;

    /**
     * The secondary_id field specifies a code that is guaranteed to be unique for
     * every document in the corpus.
     */
    protected String secondaryId;

    /**
     * The nct_id field specifies a code that is guaranteed to be unique for
     * every document in the corpus.
     */
    protected String nctId;

    /* The briefTitle field specifies a short title */
    protected String briefTitle;

    /* The acronym field specifies an acronym */
    protected String acronym;

    /* The officialTitle field specifies a long title */
    protected String officialTitle;

    /* The briefSummary field specifies a brief summary */
    protected String briefSummary;

    /* The detailedDescription field specifies a detailed description */
    protected String detailedDescription;

    /**
     * The "condition" field specifies a list of descriptive terms.
     * <p>
     * Examples Include:
     * <ol>
     * <li>Cancer
     * <li>Malnutrition
     * <li>Knee Osteoarthritis
     * </ol>
     */
    protected List<String> conditions = new ArrayList<String>();

    /**
     * The criteria field specifies the criteria for inclusion and exclusion.
     */
    protected String criteria;

    /* The gender field specifies the gender */
    protected String gender;

    /* The minimumAge field specifies a minimum age */
    protected String minimumAge;

    /* The maximumAge field specifies a maximum age */
    protected String maximumAge;

    /**
     * The "keywords" field specifies a list of descriptive terms.
     * <p>
     * Examples Include:
     * <ol>
     * <li>Geriatric
     * <li>Nutrition
     * <li>Stem cell therapy
     * </ol>
     */
    protected List<String> keywords = new ArrayList<String>();

    /**
     * The "meshTerms" field specifies a list of descriptive terms.
     * <p>
     * Examples Include:
     * <ol>
     * <li>Malnutrition
     * <li>Osteoarthritis
     * <li>Joint Diseases
     * </ol>
     */
    protected List<String> meshTerms = new ArrayList<String>();

    /**
     * Accessor for the sourceFile property.
     *
     * @return the sourceFile
     */
    public File getSourceFile() {
      return sourceFile;
    }

    /**
     * Setter for the sourceFile property.
     *
     * @param sourceFile the sourceFile to set
     */
    public void setSourceFile(File sourceFile) {
      this.sourceFile = sourceFile;
    }

    /**
     * Accessor for the org_study_id property.
     *
     * @return the org_study_id
     */
    public String getOrgStudyId() {
      return orgStudyId;
    }

    /**
     * Setter for the org_study_id property.
     *
     * @param orgStudyId the org_study_id to set
     */
    public void setOrgStudyId(String orgStudyId) {
      this.orgStudyId = orgStudyId;
    }

    /**
     * Accessor for the secondary_id property.
     *
     * @return the secondary_id
     */
    public String getSecondaryId() {
      return secondaryId;
    }

    /**
     * Setter for the secondary_id property.
     *
     * @param secondaryId the secondary_id to set
     */
    public void setSecondaryId(String secondaryId) {
      this.secondaryId = secondaryId;
    }

    /**
     * Accessor for the nct_id property.
     *
     * @return the nct_id
     */
    public String getNctId() {
      return nctId;
    }

    /**
     * Setter for the nct_id property.
     *
     * @param nctId the nct_id to set
     */
    public void setNctId(String nctId) {
      this.nctId = nctId;
    }

    /**
     * Accessor for the briefTitle property.
     *
     * @return the briefTitle
     */
    public String getBriefTitle() {
      return briefTitle;
    }

    /**
     * Setter for the briefTitle property.
     *
     * @param briefTitle the title to set
     */
    public void setBriefTitle(String briefTitle) {
      this.briefTitle = briefTitle;
    }

    /**
     * Accessor for the acronym property.
     *
     * @return the acronym
     */
    public String getAcronym() {
      return acronym;
    }

    /**
     * Accessor for the acronym property.
     *
     * @param the acronym to set
     */
    public void setAcronym(String acronym) {
      this.acronym = acronym;
    }

    /**
     * Accessor for the official_title property.
     *
     * @return the officialTitle
     */
    public String getOfficialTitle() {
      return officialTitle;
    }

    /**
     * Setter for the officialTitle property.
     *
     * @param officialTitle the title to set
     */
    public void setOfficialTitle(String officialTitle) {
      this.officialTitle = officialTitle;
    }

    /**
     * Accessor for the briefSummary property.
     *
     * @return the briefSummary
     */
    public String getBriefSummary() {
      return briefSummary;
    }

    /**
     * Setter for the briefSummary property.
     *
     * @param briefSummary the briefSummary to set
     */
    public void setBriefSummary(String briefSummary) {
      this.briefSummary = briefSummary;
    }

    /**
     * Accessor for the detailedDescription property.
     *
     * @return the detailedDescription
     */
    public String getDetailedDescription() {
      return detailedDescription;
    }

    /**
     * Setter for the detailedDescription property.
     *
     * @param detailedDescription the briefSummary to set
     */
    public void setDetailedDescription(String detailedDescription) {
      this.detailedDescription = detailedDescription;
    }

    /**
     * Accessor for the conditions property.
     *
     * @return the conditions
     */
    public List<String> getConditions() {
      return conditions;
    }

    /**
     * Setter for the conditions property.
     *
     * @param conditions the descriptors to set
     */
    public void setConditions(List<String> conditions) {
      this.conditions = conditions;
    }

    /**
     * Accessor for the criteria property.
     *
     * @return the criteria
     */
    public String getCriteria() {
      return criteria;
    }

    /**
     * Setter for the criteria property.
     *
     * @param criteria the criteria to set
     */
    public void setCriteria(String criteria) {
      this.criteria = criteria;
    }

    /**
     * Accessor for the gender property.
     *
     * @return the gender
     */
    public String getGender() {
      return gender;
    }

    /**
     * Setter for the gender property.
     *
     * @param gender the gender to set
     */
    public void setGender(String gender) {
      this.gender = gender;
    }

    /**
     * Accessor for the maximumAge property.
     *
     * @return the maximumAge
     */
    public String getMaximumAge() {
      return maximumAge;
    }

    /**
     * Setter for the maximumAge property.
     *
     * @param maximumAge the maximumAge to set
     */
    public void setMaximumAge(String maximumAge) {
      this.maximumAge = maximumAge;
    }

    /**
     * Accessor for the minimumAge property.
     *
     * @return the minimumAge
     */
    public String getMinimumAge() {
      return minimumAge;
    }

    /**
     * Setter for the minimumAge property.
     *
     * @param minimumAge the minimumAge to set
     */
    public void setMinimumAge(String minimumAge) {
      this.minimumAge = minimumAge;
    }

    /**
     * Accessor for the keywords property.
     *
     * @return the keywords
     */
    public List<String> getKeywords() {
      return keywords;
    }

    /**
     * Setter for the keywords property.
     *
     * @param keywords the descriptors to set
     */
    public void setKeywords(List<String> keywords) {
      this.keywords = keywords;
    }

    /**
     * Accessor for the conditions property.
     *
     * @return the meshTerms
     */
    public List<String> getMeshTerms() {
      return meshTerms;
    }

    /**
     * Setter for the meshTerms property.
     *
     * @param meshTerms the descriptors to set
     */
    public void setMeshTerms(List<String> meshTerms) {
      this.meshTerms = meshTerms;
    }

    /**
     * Left justify a string by forcing it to be the specified length. This is
     * done by concatonating space characters to the end of the string until the
     * string is of the specified length. If, however, the string is initially
     * longer than the specified length then the original string is returned.
     *
     * @param s a string
     * @param length the target length for the string.
     * @return a left-justified string
     */
    private String ljust(String s, Integer length) {
      if (s.length() >= length) {
        return s;
      }
      length -= s.length();
      StringBuffer sb = new StringBuffer();
      for (Integer i = 0; i < length; i++) {
        sb.append(" ");
      }
      return s + sb.toString();
    }

    @Override
    public String toString() {
      StringBuffer sb = new StringBuffer();
      appendProperty(sb, "sourceFile", sourceFile);
      appendProperty(sb, "org_study_id", orgStudyId);
      appendProperty(sb, "secondary_id", secondaryId);
      appendProperty(sb, "nct_id", nctId);
      appendProperty(sb, "acronym", acronym);
      appendProperty(sb, "briefTitle", briefTitle);
      appendProperty(sb, "officialTitle", officialTitle);
      appendProperty(sb, "briefSummary", briefSummary);
      appendProperty(sb, "detailedDescription", detailedDescription);
      appendProperty(sb, "keywords", keywords);
      appendProperty(sb, "conditions", conditions);
      appendProperty(sb, "meshTerms", meshTerms);

      return sb.toString();
    }

    /**
     * Append a property to the specified string.
     *
     * @param sb
     * @param propertyName
     * @param propertyValue
     */
    private void appendProperty(StringBuffer sb, String propertyName, Object propertyValue) {
      if (propertyValue != null) {
        propertyValue = propertyValue.toString().replaceAll("\\s+", " ").trim();
      }
      sb.append(ljust(propertyName + ":", 45) + propertyValue + "\n");
    }
  }

  /**
   * Parser for a document from a ClinicalTrials.gov Corpus
   */
  public static class Parser {
    /** CT2 Constant */
    private static final String CT2_TAG = "clinical_study";

    /** CT2 Constant */
    private static final String ID_INFO_TAG = "id_info";

    /** CT2 Constant */
    private static final String ORG_STUDY_ID_TAG = "org_study_id";

    /** CT2 Constant */
    private static final String SECONDARY_ID_TAG = "secondary_id";

    /** CT2 Constant */
    private static final String NCT_ID_TAG = "nct_id";

    /** CT2 Constant */
    private static final String BRIEF_TITLE_TAG = "brief_title";

    /** CT2 Constant */
    private static final String ACRONYM_TAG = "acronym";

    /** CT2 Constant */
    private static final String OFFICIAL_TITLE_TAG = "official_title";

    /** CT2 Constant */
    private static final String BRIEF_SUMMARY_TAG = "brief_summary";

    /** CT2 Constant */
    private static final String DETAILED_DESCRIPTION_TAG = "detailed_description";

    /** CT2 Constant */
    private static final String CONDITION_TAG = "condition";

    /** CT2 Constant */
    private static final String ELIGIBILITY_TAG = "eligibility";

    /** CT2 Constant */
    private static final String CRITERIA_TAG = "criteria";

    /** CT2 Constant */
    private static final String GENDER_TAG = "gender";

    /** CT2 Constant */
    private static final String MINIMUM_AGE_TAG = "minimum_age";

    /** CT2 Constant */
    private static final String MAXIMUM_AGE_TAG = "maximum_age";

    /** CT2 Constant */
    private static final String KEYWORD_TAG = "keyword";

    /** CT2 Constant */
    private static final String CONDITION_BROWSE_TAG = "condition_browse";

    /** CT2 Constant */
    private static final String MESH_TERM_TAG = "mesh_term";

    /** CT2 Constant */
    private static final String TEXTBLOCK_TAG = "textblock";


    public Document parseFile(BufferedReader bRdr, File fileName) throws IOException {
      RawDocument raw = parseClinicalTrialsGovCorpusDocumentFromBufferedReader(bRdr, fileName);

      Document d = new Document(raw);
      d.id = String.valueOf(raw.getNctId());
      d.contents = Stream.of(
              raw.getOfficialTitle(), raw.getBriefSummary(), raw.getDetailedDescription(), raw.getCriteria(), "\n",
              raw.getKeywords().stream().collect(Collectors.joining("\n")), "\n",
              raw.getConditions().stream().collect(Collectors.joining("\n")), "\n",
              raw.getMeshTerms().stream().collect(Collectors.joining("\n")))
              .filter(text -> text != null)
              .collect(Collectors.joining("\n"));

      return d;
    }

    /**
     * Parse an ClinicalTrials.gov Document from a file.
     *
     * @param file the file from which to parse the document
     * @param validating true if the file is to be validated against the CT2 DTD and false if it
     *   is not. It is recommended that validation be disabled, as all documents in the corpus have
     *   previously been validated against the CT2 DTD.
     * @return the parsed document, or null if an error occurs
     */
    public RawDocument parseClinicalTrialsGovCorpusDocumentFromFile(File file, boolean validating) {
      org.w3c.dom.Document document = null;
      if (validating) {
        document = loadValidating(file);
      } else {
        document = loadNonValidating(file);
      }
      return parseClinicalTrialsGovCorpusDocumentFromDOMDocument(file, document);
    }

    /**
     * Parse a ClinicalTrials.gov Document from BufferedReader. The parameter `file` is
     * used only to feed in other methods
     *
     * @param file the file from which to parse the document
     * @param bRdr the BufferedReader of file
     * @return the parsed document, or null if an error occurs
     */
    public RawDocument parseClinicalTrialsGovCorpusDocumentFromBufferedReader(BufferedReader bRdr, File file) {
      org.w3c.dom.Document document = loadFromBufferedReader(bRdr, file);

      return parseClinicalTrialsGovCorpusDocumentFromDOMDocument(file, document);
    }

    public RawDocument parseClinicalTrialsGovCorpusDocumentFromDOMDocument(File file, org.w3c.dom.Document document) {
      RawDocument ct2Document = new RawDocument();
      ct2Document.setSourceFile(file);
      NodeList children = document.getChildNodes();
      for (int i = 0; i < children.getLength(); i++) {
        Node child = children.item(i);
        String name = child.getNodeName();
        if (name.equals(CT2_TAG)) {
          handleCT2Node(child, ct2Document);
        }
      }

      return ct2Document;
    }

    private void handleCT2Node(Node node, RawDocument ct2Document) {
      NodeList children = node.getChildNodes();
      for (int i = 0; i < children.getLength(); i++) {
        Node child = children.item(i);
        String name = child.getNodeName();
        if (name.equals(ID_INFO_TAG)) {
          handleIdInfoNode(child, ct2Document);
        } else if (name.equals(BRIEF_TITLE_TAG)) {
          ct2Document.setBriefTitle(child.getTextContent());
        } else if (name.equals(ACRONYM_TAG)) {
          ct2Document.setAcronym(child.getTextContent());
        } else if (name.equals(OFFICIAL_TITLE_TAG)) {
          ct2Document.setOfficialTitle(child.getTextContent());
        } else if (name.equals(BRIEF_SUMMARY_TAG)) {
          ct2Document.setBriefSummary(child.getTextContent());
        } else if (name.equals(DETAILED_DESCRIPTION_TAG)) {
          String detailedDescription = parseTextblock(child);
          ct2Document.setDetailedDescription(detailedDescription);
        } else if (name.equals(CONDITION_TAG)) {
          ct2Document.getConditions().add(child.getTextContent());
        } else if (name.equals(ELIGIBILITY_TAG)) {
          handleEligibilityNode(child, ct2Document);
        } else if (name.equals(KEYWORD_TAG)) {
          ct2Document.getKeywords().add(child.getTextContent());
        } else if (name.equals(CONDITION_BROWSE_TAG)) {
          handleConditionBrowseNode(child, ct2Document);
        }
      }
    }

    private void handleIdInfoNode(Node node, RawDocument ct2Document) {
      NodeList children = node.getChildNodes();
      for (int i = 0; i < children.getLength(); i++) {
        Node child = children.item(i);
        String name = child.getNodeName();
        if (name.equals(ORG_STUDY_ID_TAG)) {
          ct2Document.setOrgStudyId(child.getTextContent());
        } else if (name.equals(SECONDARY_ID_TAG)) {
          ct2Document.setSecondaryId(child.getTextContent());
        } else if (name.equals(NCT_ID_TAG)) {
          ct2Document.setNctId(child.getTextContent());
        }
      }
    }

    private void handleEligibilityNode(Node node, RawDocument ct2Document) {
      NodeList children = node.getChildNodes();
      for (int i = 0; i < children.getLength(); i++) {
        Node child = children.item(i);
        String name = child.getNodeName();
        if (name.equals(CRITERIA_TAG)) {
          handleCriteriaNode(child, ct2Document);
        } else if (name.equals(GENDER_TAG)) {
          ct2Document.setGender(child.getTextContent());
        } else if (name.equals(MINIMUM_AGE_TAG)) {
          ct2Document.setMinimumAge(child.getTextContent());
        } else if (name.equals(MAXIMUM_AGE_TAG)) {
          ct2Document.setMaximumAge(child.getTextContent());
        }
      }
    }

    private void handleCriteriaNode(Node node, RawDocument ct2Document) {
      String criteria = parseTextblock(node);
      ct2Document.setCriteria(criteria.trim());
    }

    private void handleConditionBrowseNode(Node node, RawDocument ct2Document) {
      NodeList children = node.getChildNodes();
      for (int i = 0; i < children.getLength(); i++) {
        Node child = children.item(i);
        String name = child.getNodeName();
        if (name.equals(MESH_TERM_TAG)) {
          ct2Document.getMeshTerms().add(child.getTextContent());
        }
      }
    }

    /**
     * Load a document from a BufferedReader without validating it.
     * @param bRdr the BufferedReader that data read in
     * @param file the file that data stored in
     * @return the parsed document or null if an error occurs
     */
    private org.w3c.dom.Document loadFromBufferedReader(BufferedReader bRdr, File file) {
      org.w3c.dom.Document document;
      StringBuffer sb = new StringBuffer();
      try {
        String line;
        while ((line = bRdr.readLine()) != null) {
          sb.append(line + "\n");
        }
        String xmlData = sb.toString();
        document = parseStringToDOM(xmlData, "UTF-8", file);
        return document;
      } catch (IOException e) {
        LOG.error("Error loading file " + file + ".");
      }
      return null;
    }

    /**
     * Load a document without validating it. Since instructing the java.xml
     * libraries to do this does not actually disable validation, this method
     * disables validation by removing the doctype declaration from the XML
     * document before it is parsed.
     *
     * @param file the file to parse
     * @return the parsed document or null if an error occurs
     */
    private org.w3c.dom.Document loadNonValidating(File file) {
      org.w3c.dom.Document document;
      StringBuffer sb = new StringBuffer();
      try {
        BufferedReader in = new BufferedReader(new InputStreamReader(
            new FileInputStream(file), "UTF8"));
        String line = null;
        while ((line = in.readLine()) != null) {
          sb.append(line + "\n");
        }
        String xmlData = sb.toString();
        document = parseStringToDOM(xmlData, "UTF-8", file);
        in.close();
        return document;
      } catch (UnsupportedEncodingException e) {
        //e.printStackTrace();
        LOG.error("Error loading file " + file + ".");
      } catch (FileNotFoundException e) {
        //e.printStackTrace();
        LOG.error("Error loading file " + file + ".");
      } catch (IOException e) {
        //e.printStackTrace();
        LOG.error("Error loading file " + file + ".");
      }
      return null;
    }

    /**
     * Parse the specified file into a DOM Document.
     *
     * @param file the file to parse.
     * @return the parsed DOM Document or null if an error occurs
     */
    private org.w3c.dom.Document loadValidating(File file) {
      try {
        return getDOMObject(file.getAbsolutePath(), true);
      } catch (SAXException e) {
        //e.printStackTrace();
        LOG.error("Error parsing digital document from nitf file "
            + file + ".");
      } catch (IOException e) {
        //e.printStackTrace();
        LOG.error("Error parsing digital document from nitf file "
            + file + ".");
      } catch (ParserConfigurationException e) {
        //e.printStackTrace();
        LOG.error("Error parsing digital document from nitf file "
            + file + ".");
      }
      return null;
    }

    /**
     * Parse a string to a DOM document.
     *
     * @param s a string containing an XML document
     * @return the DOM document if it can be parsed, or null otherwise
     */
    private org.w3c.dom.Document parseStringToDOM(String s, String encoding, File file) {
      try {
        DocumentBuilderFactory factory = DocumentBuilderFactory
            .newInstance();
        factory.setValidating(false);
        InputStream is = new ByteArrayInputStream(s.getBytes(encoding));
        org.w3c.dom.Document doc = factory.newDocumentBuilder().parse(is);
        is.close();
        return doc;
      } catch (SAXException e) {
        //e.printStackTrace();
        LOG.error("Exception processing file " + file + ".");
      } catch (ParserConfigurationException e) {
        //e.printStackTrace();
        LOG.error("Exception processing file " + file + ".");
      } catch (IOException e) {
        //e.printStackTrace();
        LOG.error("Exception processing file " + file + ".");
      }
      return null;
    }

    /**
     * Parse a file containing an XML document, into a DOM object.
     *
     * @param filename a path to a valid file
     * @param validating true iff validating should be turned on
     * @return a DOM Object containing a parsed XML document or a null value if there
     *   is an error in parsing
     * @throws ParserConfigurationException if error encountered
     * @throws IOException if error encountered
     * @throws SAXException if error encountered
     */
    private org.w3c.dom.Document getDOMObject(String filename, boolean validating)
        throws SAXException, IOException, ParserConfigurationException {
      // Create a builder factory

      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      if (!validating) {
        factory.setValidating(validating);
        factory.setSchema(null);
        factory.setNamespaceAware(false);
      } else {
        SchemaFactory schemaFactory = SchemaFactory.newInstance("http://www.w3.org/2001/XMLSchema");
        factory.setSchema(schemaFactory.newSchema(new URL("https://clinicaltrials.gov/ct2/html/images/info/public.xsd")));
      }

      DocumentBuilder builder = factory.newDocumentBuilder();
      // Create the builder and parse the file
      org.w3c.dom.Document doc = builder.parse(new File(filename));
      return doc;
    }

    private String parseTextblock(Node node) {
      StringBuffer sb = new StringBuffer();
      NodeList children = node.getChildNodes();
      for (int i = 0; i < children.getLength(); i++) {
        Node child = children.item(i);
        String name = child.getNodeName();
        if (name.equals(TEXTBLOCK_TAG)) {
          sb.append(getAllText(child).trim() + "\n");
        }
      }

      if (sb.length() > 0) {
        sb.setLength(sb.length() - 1);
        String returnVal = sb.toString();
        return returnVal.length() > 0 ? returnVal : null;
      }
      return null;
    }

    private String getAttributeValue(Node node, String attributeName) {
      NamedNodeMap attributes = node.getAttributes();
      if (attributes != null) {
        Node attribute = attributes.getNamedItem(attributeName);
        if (attribute != null) {
          return attribute.getNodeValue();
        }
      }
      return null;
    }

    private String getAllText(Node node) {
      List<Node> textNodes = getNodesByTagName(node, "#text");
      StringBuffer sb = new StringBuffer();
      for (Node textNode : textNodes) {
        sb.append(textNode.getNodeValue().trim() + " ");
      }
      return sb.toString().trim();
    }

    private List<Node> getNodesByTagName(Node node, String tagName) {
      List<Node> matches = new ArrayList<Node>();
      recursiveGetNodesByTagName(node, tagName.toLowerCase(), matches);
      return matches;
    }

    private void recursiveGetNodesByTagName(Node node, String tagName,
                                            List<Node> matches) {
      String name = node.getNodeName();
      if (name != null && name.toLowerCase().equals(tagName)) {
        matches.add(node);
      }
      if (node.getChildNodes() != null
          && node.getChildNodes().getLength() > 0) {
        for (int i = 0; i < node.getChildNodes().getLength(); i++) {
          recursiveGetNodesByTagName(node.getChildNodes().item(i),
              tagName, matches);
        }
      }
    }
  }
}
