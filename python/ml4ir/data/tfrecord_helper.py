from tensorflow import train
import tensorflow as tf
from ml4ir.config.keys import TFRecordTypeKey


def _bytes_feature(values):
    """Returns a bytes_list from a string / byte."""
    values = [value.encode("utf-8") for value in values]
    return train.Feature(bytes_list=train.BytesList(value=values))


def _float_feature(values):
    """Returns a float_list from a float / double."""
    return train.Feature(float_list=train.FloatList(value=values))


def _int64_feature(values):
    """Returns an int64_list from a bool / enum / int / uint."""
    return train.Feature(int64_list=train.Int64List(value=values))


def _get_feature_fn(dtype):
    """Returns appropriate feature function based on datatype"""
    if dtype == tf.string:
        return _bytes_feature
    elif dtype == tf.float32:
        return _float_feature
    elif dtype == tf.int64:
        return _int64_feature
    else:
        raise Exception("Feature dtype {} not supported".format(dtype))


def get_sequence_example_proto(group, context_features, sequence_features):
    """
    Get a sequence example protobuf from a dataframe group

    Args:
        - group: pandas dataframe group
        - context_features: feature configuration for context
        - sequence_features: feature configuration for sequence
    """
    sequence_features_dict = dict()
    context_features_dict = dict()

    for feature_info in context_features:
        feature_name = feature_info["name"]
        feature_fn = _get_feature_fn(feature_info["dtype"])
        context_features_dict[feature_name] = feature_fn([group[feature_name].tolist()[0]])

    for feature_info in sequence_features:
        feature_name = feature_info["name"]
        feature_fn = _get_feature_fn(feature_info["dtype"])
        if feature_info["tfrecord_type"] == TFRecordTypeKey.SEQUENCE:
            sequence_features_dict[feature_name] = train.FeatureList(
                feature=[feature_fn(group[feature_name].tolist())]
            )

    sequence_example_proto = train.SequenceExample(
        context=train.Features(feature=context_features_dict),
        feature_lists=train.FeatureLists(feature_list=sequence_features_dict),
    )

    return sequence_example_proto
