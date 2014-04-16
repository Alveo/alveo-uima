# UIMA-vlab

An interface to [HCS vLab][hvl] for the [Apache UIMA Framework][uima].

[hvl]: http://hcsvlab.org.au/
[uima]: http://uima.apache.org/

## Purpose

This package provides a translation layer between the REST API of HCS vLab and the UIMA framework. For reading the documents and associated annotations from vLab, this is implemented as a UIMA [Collection Reader][uimacr] -- that is, a component which produces UIMA documents which are then available for subsequent processing, generally in a [Collection Processing Engine (CPE)][uimacpe].

[uimacr]: http://uima.apache.org/d/uimaj-2.4.2/tutorials_and_users_guides.html#ugr.tug.cpe.collection_reader.developing
[uimacpe]: http://uima.apache.org/d/uimaj-2.4.2/tutorials_and_users_guides.html#ugr.tug.cpe

## Building

The project uses a fairly standard Maven build setup. Build using

    $ mvn compile


## Usage

This is built using [UIMAfit][uimafit], a collection of tools to allow more flexibility and simplicity in creating and configuring UIMA processing pipelines. This means that it can most easily be run directly from Java code. The point of interaction for reading a vLab-based collection is the class `au.edu.alveo.uima.ItemListCollectionReader`

[uimafit]: http://uima.apache.org/uimafit.html

An example of usage of this class can be found in `src/main/java/com/nicta/uimavlab/examples/ItemListCollectionReaderExample.java`. This main class takes the following as arguments (run the class with no arguments for more detailed usage information):

  * a server URI
  * an API key
  * an item list ID
  * an output directory

It creates a UIMA pipeline (with UIMAfit, rather than an XML-based CPE) using the collection reader and an extra processing component which just serializes the documents output by the collection reader to disk (in a real-world pipeline, we might want to do more at this stage). You can then manually examine the created XML from the output directory, or run the Annotation Viewer GUI (`org.apache.uima.tools.AnnotationViewerMain`), specifying  `typesystem.xml` which will have been written the root of the output directory as the type system.

For a more traditional workflow based on CPEs defined by XML descriptors, there is an XML-descriptor for the Collection Reader which is automatically written to `target/generated-sources/uimafit/com/nicta/uimavlab/ItemListCollectionReader.xml` when `mvn package` is run. However currently this doesn't produce a type system in the XML (as documented in an [open issue][uimafit-ts-issue]), so there will soon be a class to manually create these descriptors if they are needed (for now, a fifth argument to `au.edu.alveo.uima.examples.ItemListReaderExample` can be used to specify a directory to write the preconfigured descriptors to). These descriptors can then be used to manually create a CPE (by writing XML), or by running the CPE configurator GUI (`org.apache.uima.tools.cpm.CpmFrame`).

[uimafit-ts-issue]: https://issues.apache.org/jira/browse/UIMA-3346