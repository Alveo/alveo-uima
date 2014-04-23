# Alveo-UIMA

An interface to [Alveo][alv] for the [Apache UIMA Framework][uima].

[alv]: http://alveo.edu.au/
[uima]: http://uima.apache.org/

## Purpose

This package provides a translation layer between the REST API of Alveo
and the UIMA framework. For reading the documents and associated
annotations from Alveo, this is implemented as a UIMA [Collection
Reader][uimacr] -- that is, a component which produces UIMA documents
which are then available for subsequent processing, generally in a
[Collection Processing Engine (CPE)][uimacpe].

[uimacr]: http://uima.apache.org/d/uimaj-2.4.2/tutorials_and_users_guides.html#ugr.tug.cpe.collection_reader.developing
[uimacpe]: http://uima.apache.org/d/uimaj-2.4.2/tutorials_and_users_guides.html#ugr.tug.cpe

## Building

The project uses a fairly standard Maven build setup. Build using

    $ mvn compile


## Usage

### Using UIMAfit


This is built using [UIMAfit][uimafit], a collection of tools to allow
more flexibility and simplicity in creating and configuring UIMA
processing pipelines. This means that it can most easily be run
directly from Java code. The point of interaction for reading an Alveo-based collection is the class
`au.edu.alveo.uima.ItemListCollectionReader`

[uimafit]: http://uima.apache.org/uimafit.html

#### Reading Annotations

An example of usage of this class can be found in
`src/main/java/au/edu/alveo/uima/examples/ItemListCollectionReaderExample.java`.
This main class takes the following as arguments (run the class with no
arguments for more detailed usage information):

  * a server URI
  * an API key
  * an item list ID
  * an output directory

It creates a UIMA pipeline (with UIMAfit, rather than an XML-based CPE)
using the collection reader and an extra processing component which
just serializes the documents output by the collection reader to disk
(in a real-world pipeline, we might want to do more at this stage). You
can then manually examine the created XML from the output directory, or
run the Annotation Viewer GUI
(`org.apache.uima.tools.AnnotationViewerMain`), specifying
`typesystem.xml` which will have been written the root of the output
directory, as the type system.

#### Uploading Annotations

The class `au.edu.alveo.uima.ItemAnnotationUploader` allows the inverse
operation – annotations provided by other UIMA components can be
uploaded to the Alveo server. The expected usage for this is that it
would be part of a pipeline, with the `ItemListCollectionReader`
instance as the collection reader, and any other desired processing
components would be inserted into the pipeline before instantiating the
annotation uploader.

Here is an example of how you could use UIMAfit to run an uploading
pipeline, which augments the items with POS tags from the the [OpenNLP
POS tagger][onlppos] annotator of [DKPro][dkpro]:

[onlppos]: http://dkpro-core-asl.googlecode.com/svn/de.tudarmstadt.ukp.dkpro.core-asl/tags/latest-release/apidocs/de/tudarmstadt/ukp/dkpro/core/opennlp/OpenNlpPosTagger.html
[DKPro]: https://code.google.com/p/dkpro-core-asl/

	/**
	 * Run a pipeline which adds POS tags and sentence boundaries to the items.
	 *
	 * @param serverUrl  The base URL of the Alveo server
	 * @param apiKey     The API key for the Alveo server
	 * @param itemListId The ID of the item list to read from the server
	 */
	public static void runPipeline(String serverUrl, String apiKey, String itemListId)
			throws UIMAException, IOException {
		CollectionReaderDescription reader = ItemListCollectionReader.createDescription(
				ItemListCollectionReader.PARAM_ALVEO_BASE_URL, serverUrl,
				ItemListCollectionReader.PARAM_ALVEO_API_KEY, apiKey,
				ItemListCollectionReader.PARAM_ALVEO_ITEM_LIST_ID, itemListId,
				ItemListCollectionReader.PARAM_INCLUDE_RAW_DOCS, false);
		AnalysisEngineDescription segmenter = AnalysisEngineFactory.createEngineDescription(OpenNlpSegmenter.class);
		AnalysisEngineDescription posTagger = AnalysisEngineFactory.createEngineDescription(OpenNlpPosTagger.class);

        /* Set the names of features which will be used by default to populate the Alveo label values
        * More complicated mappings are possible by implementing
        * au.edu.alveo.uima.conversions.UIMAToAlveoAnnConverter and supplying the
        * name of that class in parameter ItemListCollectionReader.PARAM_ANNOTATION_CONVERTERS */
		String[] labelFeatures = new String[] {
				"de.tudarmstadt.ukp.dkpro.core.api.lexmorph.type.pos.POS:PosValue",
				ItemAnnotationUploader.DEFAULT_LABEL_FEATURE
		};
		// Set the names of types we wish to upload to the server. Other types are ignored.
		String[] uploadableTypes = new String[] {
				"de.tudarmstadt.ukp.dkpro.core.api.lexmorph.type.pos.POS",
				"de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Sentence"
		};
		AnalysisEngineDescription uploader = AnalysisEngineFactory.createEngineDescription(ItemAnnotationUploader.class,
				ItemAnnotationUploader.PARAM_ALVEO_BASE_URL, serverUrl,
				ItemAnnotationUploader.PARAM_ALVEO_API_KEY, apiKey,
				ItemAnnotationUploader.PARAM_LABEL_FEATURE_NAMES, labelFeatures,
				ItemAnnotationUploader.PARAM_UPLOADABLE_UIMA_TYPE_NAMES, uploadableTypes);
		AnalysisEngineDescription aggAe = AnalysisEngineFactory.createEngineDescription(segmenter, posTagger, uploader);
		SimplePipeline.runPipeline(reader, aggAe);
	}

The UIMA annotations are converted to Alveo format using
`au.edu.alveo.uima.conversions.DefaultUIMAToAlveoAnnConverter` by
default, which attempts to populate the `type` and `label` features
sensibly. See the class documentation for more details of how this
works.

More information on adding annotations using UIMA can be found in the more extensive examples in the [Alveo UIMA tutorial][aut].

[aut]: https://bitbucket.org/andymackinlay/alveo-uima-tutorial

### Using XML-based descriptors

For a more traditional workflow based on CPEs defined by XML
descriptors, there is an XML-descriptor for the Collection Reader which
is automatically written to
`target/generated-sources/uimafit/au/edu/alveo/uima/ItemListCollectionReader.xml`
when `mvn package` is run. However currently this doesn't include a
valid type system for two reasons. One is that there is an [open
issue][uimafit-ts-issue] which prevents this. The second is that the
type system needs to be dynamically generated by talking to a live
server (since we can't know all the types without talking to the
server) so the Maven plugin which does the auto-generation wouldn't
help.

For this reason, there is a class
`au.edu.alveo.uima.utils.WriteDynamicDescriptors` which can be manually
invoked from the command-line to create these descriptors. These
descriptors can then be used to manually create a CPE (by writing XML),
or by running the CPE configurator GUI
(`org.apache.uima.tools.cpm.CpmFrame`).

[uimafit-ts-issue]: https://issues.apache.org/jira/browse/UIMA-3346