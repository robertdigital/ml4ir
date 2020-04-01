package ml4ir.inference.tensorflow.data

import com.google.common.base.Charsets
import com.google.protobuf.ByteString
import org.tensorflow.example._
import java.lang.{Float => JFloat, Long => JLong}
import java.util.{Map => JMap}
import java.util.function.{Function => JFunction}

import com.google.common.collect.Maps

import scala.collection.JavaConverters._
import scala.reflect.ClassTag

abstract class SequenceExampleBuilderBase[C, S](contextFeaturesPreprocessor: FeaturePreprocessor[C],
                                                sequenceFeaturesPreprocessor: FeaturePreprocessor[S]) {
  type FnMap[T] = JMap[String, JFunction[_ >: T, _ <: T]]
  val sequenceExampleBuilder = SequenceExampleBuilder()

  def apply(context: C, sequence: List[S]): SequenceExample =
    sequenceExampleBuilder(contextFeaturesPreprocessor(context), sequence.map(sequenceFeaturesPreprocessor).toArray)

  def build(context: C, sequence: java.util.List[S]): SequenceExample = apply(context, sequence.asScala.toList)
}

case class StringMapSequenceExampleBuilder(modelFeatures: ModelFeatures,
                                           primitiveProcessors: Map[String, PrimitiveProcessor] =
                                             Map.empty.withDefaultValue(PrimitiveProcessor())
                                           /*,
                                           floatProcessors: FnMap[Float] = Maps.newHashMap(),
                                           longProcessors: FnMap[Long] = Maps.newHashMap(),
                                           stringProcessors: FnMap[String] = Maps.newHashMap()*/ )
    extends SequenceExampleBuilderBase[JMap[String, String], JMap[String, String]](
      StringMapFeatureProcessor(modelFeatures, "context", primitiveProcessors /*, floatProcessors*/ ),
      StringMapFeatureProcessor(modelFeatures, "sequence", primitiveProcessors /*, floatProcessors*/ )
    )

object StringMapSequenceExampleBuilder {
  def simple(modelFeatures: ModelFeatures) = StringMapSequenceExampleBuilder(modelFeatures)

  def withFeatureProcessors(modelFeatures: ModelFeatures,
                            primitiveProcessors: Map[String, PrimitiveProcessor]
                            /*floatProcessors: FnMap[Float],
                            longProcessors: FnMap[Long],
                            stringProcessors: FnMap[String]*/ ) =
    StringMapSequenceExampleBuilder(modelFeatures,
                                    primitiveProcessors /*floatProcessors, longProcessors, stringProcessors*/ )
}

/**
  * Builder class for more easily instantiating SequenceExample protobufs from raw(-ish) features
  */
case class SequenceExampleBuilder() {

  /**
    * Functional API allowing the builder to act like a function to transform query/documents into a scorable protobuf
    * @param context struct primarily containing the query text
    * @param docs array of document-feature structs
    * @return TensorFlow's protobuf structure containing the raw features in one SequenceExample packet
    */
  def apply(context: Example, docs: Array[Example]): SequenceExample = {
    val contextFeatures: Features = buildMultiFeatures(context.features)
    val docFeatures = buildMultiFeatureLists(docs.map(_.features))
    SequenceExample
      .newBuilder()
      .setContext(contextFeatures)
      .setFeatureLists(docFeatures)
      .build()
  }

  private def buildMultiFeatures(features: MultiFeatures): Features = {
    val withStringFeatures = features.stringFeatures
      .foldLeft(Features.newBuilder()) {
        case (bldr, (nodeName: String, stringFeature: String)) =>
          bldr.putFeature(nodeName, toFeature(stringFeature))
      }
    val withStringAndFloatFeatures = features.floatFeatures
      .foldLeft(withStringFeatures) {
        case (bldr, (nodeName: String, floatFeature: Float)) =>
          bldr.putFeature(nodeName, toFeature(floatFeature))
      }
    val withFloatsAndIntsAndStrings = features.int64Features
      .foldLeft(withStringAndFloatFeatures) {
        case (bldr, (nodeName: String, longFeature: Long)) =>
          bldr.putFeature(nodeName, toFeature(longFeature))
      }
    withFloatsAndIntsAndStrings.build()
  }

  private def buildMultiFeatureLists(features: Array[MultiFeatures]): FeatureLists = {
    val withFloats = transpose(features.map(_.floatFeatures))
      .foldLeft(FeatureLists.newBuilder()) {
        case (bldr, (name: String, featureValues: Array[Float])) =>
          bldr.putFeatureList(name, floats(featureValues.map(JFloat.valueOf)))
      }
    val withFloatsAndInts =
      transpose(features.map(_.int64Features))
        .foldLeft(withFloats) {
          case (bldr, (name: String, featureValues: Array[Long])) =>
            bldr.putFeatureList(name, longs(featureValues.map(JLong.valueOf)))
        }
    val withFloatsAndIntsAndStrings =
      transpose(features.map(_.stringFeatures))
        .foldLeft(withFloatsAndInts) {
          case (bldr, (name: String, featureValues: Array[String])) =>
            bldr.putFeatureList(name, strings(featureValues))
        }
    withFloatsAndIntsAndStrings.build()
  }

  /**
    * Effectively transforms an array of maps of features into a map of arrays of features: the "transpose" operation
    * @param docFeatures to have their features extracted out into one dense array per feature
    * @return map of feature-name -> padded dense vector of numeric features
    */
  private def transpose[T: ClassTag](
      docFeatures: Array[Map[String, T]]
  ): Map[String, Array[T]] = {
    // TODO: check that we don't need to pad anymore
    // val numDocsPerQuery = config.numDocsPerQuery.getOrElse(docFeatures.length)
    case class FeatureVal(name: String, value: T, docIdx: Int)
    val featureSet: Set[String] = docFeatures.map(_.keySet).reduce(_ union _)
    docFeatures
      .slice(0, docFeatures.length) // math.min(docFeatures.length, numDocsPerQuery)) // this was for padding
      .zipWithIndex
      .flatMap {
        case (doc: Map[String, T], idx: Int) =>
          featureSet.map(name => FeatureVal(name, doc(name), idx))
      }
      .groupBy(_.name)
      .mapValues(_.sortBy(_.docIdx).map(_.value).toArray)
  }

  /**
    *
    * @param featureValues
    * @return
    */
  private def toFeature(featureValues: Array[JLong]): Feature = {
    Feature
      .newBuilder()
      .setInt64List(
        Int64List.newBuilder().addAllValue(featureValues.toList.asJava)
      )
      .build()
  }

  /**
    *
    * @param featureValues
    * @return
    */
  private def toFeature(featureValues: Array[JFloat]): Feature = {
    Feature
      .newBuilder()
      .setFloatList(
        FloatList.newBuilder().addAllValue(featureValues.toList.asJava)
      )
      .build()
  }

  /**
    *
    * @param featureValues
    * @return
    */
  private def toFeature(featureValues: Array[String]): Feature = {
    Feature
      .newBuilder()
      .setBytesList(
        BytesList
          .newBuilder()
          .addAllValue(
            featureValues.toList
              .map(a => ByteString.copyFrom(a.getBytes))
              .asJava
          )
          .build()
      )
      .build()
  }

  /**
    *
    * @param featureValues
    * @return
    */
  private def longs(featureValues: Array[JLong]): FeatureList = {
    FeatureList
      .newBuilder()
      .addFeature(toFeature(featureValues))
      .build()
  }

  /**
    *
    * @param featureValues
    * @return
    */
  private def floats(featureValues: Array[JFloat]): FeatureList = {
    FeatureList
      .newBuilder()
      .addFeature(toFeature(featureValues))
      .build()
  }

  /**
    *
    * @param featureValues
    * @return
    */
  private def strings(featureValues: Array[String]): FeatureList = {
    FeatureList
      .newBuilder()
      .addFeature(toFeature(featureValues))
      .build()
  }

  private def toFeature(stringFeature: String): Feature = {
    Feature
      .newBuilder()
      .setBytesList(
        BytesList
          .newBuilder()
          .addValue(ByteString.copyFrom(stringFeature.getBytes(Charsets.UTF_8)))
          .build()
      )
      .build()
  }

  /**
    *
    * @param floatFeature
    * @return
    */
  private def toFeature(floatFeature: Float): Feature = {
    Feature
      .newBuilder()
      .setFloatList(
        FloatList
          .newBuilder()
          .addValue(floatFeature)
          .build()
      )
      .build()
  }

  /**
    *
    * @param longFeature
    * @return
    */
  private def toFeature(longFeature: Long): Feature = {
    Feature
      .newBuilder()
      .setInt64List(
        Int64List
          .newBuilder()
          .addValue(longFeature)
          .build()
      )
      .build()
  }
}