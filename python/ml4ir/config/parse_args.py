from argparse import ArgumentParser, Namespace
from typing import List


def define_args() -> ArgumentParser:

    parser = ArgumentParser(description="Process arguments for ml4ir ranking pipeline.")

    parser.add_argument(
        "--data_dir",
        type=str,
        default=None,
        help="Path to the data directory to be used for training and inference. "
        "Can optionally include train/ val/ and test/ subdirectories. "
        "If subdirectories are not present, data will be split based on train_pcent_split",
    )

    parser.add_argument(
        "--data_format",
        type=str,
        default="tfrecord",
        help="Format of the data to be used. "
        "Should be one of the Data format keys in ml4ir/config/keys.py",
    )

    parser.add_argument(
        "--feature_config",
        type=str,
        default=None,
        help="Path to JSON file pr JSON string with feature metadata for training.",
    )

    parser.add_argument(
        "--model_file",
        type=str,
        default="",
        help="Path to a pretrained model to load for either resuming training or for running in"
        "inference mode.",
    )

    parser.add_argument(
        "--architecture",
        type=str,
        default="simple_dnn",
        help="Model to use for training. Has to be one of the keys in ArchitectureKey under "
        "ml4ir/config/keys.py",
    )

    parser.add_argument(
        "--optimizer",
        type=str,
        default="adam",
        help="Optimizer to use. Has to be one of the optimizers in OptimizerKey under "
        "ml4ir/config/keys.py",
    )

    parser.add_argument(
        "--metrics",
        type=str,
        default="MRR",
        help="Metric to compute. Can be a list. Has to be one of the metrics in MetricKey under "
        "ml4ir/config/keys.py",
    )

    parser.add_argument(
        "--loss",
        type=str,
        default="sigmoid_cross_entropy",
        help="Loss to optimize. Has to be one of the losses in LossKey under ml4ir/config/keys.py",
    )

    parser.add_argument(
        "--scoring",
        type=str,
        default="pointwise",
        help="Scoring technique to use. Has to be one of the scoring types in ScoringKey in "
        "ml4ir/config/keys.py",
    )

    parser.add_argument(
        "--num_epochs",
        type=int,
        default=5,
        help="Max number of training epochs(or full pass over the data)",
    )

    parser.add_argument(
        "--batch_size", type=int, default=128, help="Number of data samples to use per batch."
    )

    parser.add_argument("--learning_rate", type=float, default=0.01, help="Step size (e.g.: 0.01)")

    parser.add_argument(
        "--learning_rate_decay", type=float, default=0.96, help="decay rate for the learning rate"
    )

    parser.add_argument(
        "--compute_intermediate_stats",
        type=bool,
        default=True,
        help="Whether to compute intermediate stats on test set (mrr, acr, etc) (slow)",
    )

    parser.add_argument(
        "--execution_mode",
        type=str,
        default="train_evaluate",
        help="Execution mode for the pipeline. Should be one of ExecutionModeKey",
    )

    parser.add_argument(
        "--random_state",
        type=int,
        default=123,
        help="Initialize the seed to control randomness for replication",
    )

    parser.add_argument(
        "--run_id",
        type=str,
        default="",
        help="Run ID for the current training. Autogenerated if not specified.",
    )

    parser.add_argument(
        "--models_dir",
        type=str,
        default="models/",
        help="Path to save the model. Will be expanded to models_dir/run_id",
    )

    parser.add_argument(
        "--logs_dir",
        type=str,
        default="logs/",
        help="Path to save the training/inference logs. Will be expanded to logs_dir/run_id",
    )

    parser.add_argument(
        "--checkpoint_model",
        type=bool,
        default=True,
        help="Whether to save model checkpoints at the end of each epoch. Recommended - set to True",
    )

    parser.add_argument(
        "--train_pcent_split",
        type=float,
        default=0.8,
        help="Percentage of all data to be used for training. The remaining is used for validation and "
        "testing. Remaining data is split in half if val_pcent_split or test_pcent_split are not "
        "specified.",
    )

    parser.add_argument(
        "--val_pcent_split",
        type=float,
        default=-1,
        help="Percentage of all data to be used for testing.",
    )

    parser.add_argument(
        "--test_pcent_split",
        type=float,
        default=-1,
        help="Percentage of all data to be used for testing.",
    )

    parser.add_argument(
        "--max_num_records",
        type=int,
        default=25,
        help="Maximum number of records per query considered for ranking.",
    )

    parser.add_argument(
        "--inference_signature",
        type=str,
        default="serving_default",
        help="SavedModel signature to be used for inference",
    )

    return parser


def get_args(args: List[str]) -> Namespace:
    parser = define_args()
    return parser.parse_args(args)
