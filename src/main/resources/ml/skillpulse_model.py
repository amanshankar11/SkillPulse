import numpy as np
from datetime import datetime, timedelta
from sklearn.ensemble import GradientBoostingClassifier
from sklearn.model_selection import train_test_split
from sklearn.metrics import accuracy_score


# ==========================================================
# 1. HALF-LIFE REGRESSION (Memory Decay Model)
# ==========================================================

class HalfLifeRegression:

    def __init__(self):
        self.weights = np.array([0.4, 0.3, -0.2, -0.1])

    def compute_half_life(self, features):
        log_h = np.dot(features, self.weights)
        return np.clip(np.exp(log_h), 0.5, 90)

    def recall_probability(self, features, days_gap):
        h = self.compute_half_life(features)
        return 2 ** (-days_gap / h)


# ==========================================================
# 2. DEEP KNOWLEDGE TRACING (Simplified)
# ==========================================================

class SimpleDKT:

    def __init__(self, dim=20):
        self.state = np.zeros(dim)
        self.decay_rate = 0.05

    def update(self, skill_id, correct, time_gap):
        decay = np.exp(-self.decay_rate * time_gap)
        self.state *= decay

        idx = skill_id % len(self.state)

        if correct:
            self.state[idx] += 0.1 * (1 - self.state[idx])
        else:
            self.state[idx] *= 0.8

        self.state = np.clip(self.state, 0, 1)

    def predict(self, skill_id):
        idx = skill_id % len(self.state)
        return self.state[idx]


# ==========================================================
# 3. FEATURE EXTRACTOR (Unified Schema)
# ==========================================================

class FeatureExtractor:

    FEATURE_ORDER = [
        "days_gap",
        "freq_7d",
        "freq_30d",
        "avg_gap",
        "recent_acc",
        "overall_acc",
        "trend",
        "syntax_err",
        "logic_err",
        "difficulty",
        "hints",
        "completion",
        "hlr_prob",
        "dkt_strength"
    ]

    def __init__(self):
        self.hlr = HalfLifeRegression()
        self.dkt = SimpleDKT()

    def extract(self, history):

        timestamps = history["timestamps"]
        accuracies = history["accuracies"]

        now = datetime.now()

        days_gap = (now - timestamps[-1]).days

        freq_7d = sum(1 for t in timestamps if (now - t).days <= 7)
        freq_30d = sum(1 for t in timestamps if (now - t).days <= 30)

        if len(timestamps) > 1:
            gaps = [(timestamps[i] - timestamps[i - 1]).days
                    for i in range(1, len(timestamps))]
            avg_gap = np.mean(gaps)
        else:
            avg_gap = 0

        recent_acc = np.mean(accuracies[-5:])
        overall_acc = np.mean(accuracies)

        if len(accuracies) > 1:
            trend = np.polyfit(range(len(accuracies)), accuracies, 1)[0]
        else:
            trend = 0

        total = len(accuracies)
        syntax_err = history["errors"].get("syntax", 0) / total
        logic_err = history["errors"].get("logic", 0) / total

        difficulty = np.mean(history["difficulties"])
        hints = np.mean(history["hints_used"])
        completion = np.mean(history["completion_status"])

        # ---- HLR ----
        hlr_features = np.array([freq_30d, recent_acc, trend, difficulty])
        hlr_prob = self.hlr.recall_probability(hlr_features, days_gap)

        # ---- DKT ----
        self.dkt = SimpleDKT()  # reset state
        for i in range(len(accuracies)):
            gap = 0 if i == 0 else (timestamps[i] - timestamps[i - 1]).days
            self.dkt.update(skill_id=0,
                            correct=1 if accuracies[i] > 0.6 else 0,
                            time_gap=gap)

        dkt_strength = self.dkt.predict(skill_id=0)

        features = [
            days_gap, freq_7d, freq_30d, avg_gap,
            recent_acc, overall_acc, trend,
            syntax_err, logic_err,
            difficulty, hints, completion,
            hlr_prob, dkt_strength
        ]

        return np.array(features)


# ==========================================================
# 4. DECAY CLASSIFIER
# ==========================================================

class DecayClassifier:

    def __init__(self):
        self.model = GradientBoostingClassifier(random_state=42)
        self.trained = False

    def train(self, X, y):
        self.model.fit(X, y)
        self.trained = True

    def predict(self, X):
        return self.model.predict(X)

    def evaluate(self, X, y):
        preds = self.predict(X)
        return accuracy_score(y, preds)


# ==========================================================
# 5. FULL SKILLPULSE SYSTEM
# ==========================================================

class SkillPulseSystem:

    def __init__(self):
        self.extractor = FeatureExtractor()
        self.classifier = DecayClassifier()

    def train(self, histories, labels):
        X = np.array([self.extractor.extract(h) for h in histories])
        self.classifier.train(X, labels)

    def analyze(self, history):
        features = self.extractor.extract(history)
        pred = self.classifier.predict(features.reshape(1, -1))[0]

        status_map = {0: "IMPROVING", 1: "STABLE", 2: "DECAYING"}
        return status_map[pred]


# ==========================================================
# 6. DEMO DATA
# ==========================================================

def generate_user(decay=False):

    now = datetime.now()

    timestamps = [now - timedelta(days=i * 3) for i in range(10)]

    if decay:
        accuracies = np.linspace(0.9, 0.4, 10)
        label = 2
    else:
        accuracies = np.linspace(0.5, 0.9, 10)
        label = 0

    user = {
        "timestamps": timestamps,
        "accuracies": accuracies,
        "difficulties": [2.0] * 10,
        "errors": {"syntax": 3, "logic": 2},
        "hints_used": [1] * 10,
        "completion_status": [1] * 10
    }

    return user, label


# ==========================================================
# 7. MAIN
# ==========================================================

def main():

    print("===== SKILLPULSE (5 USERS DEMO) =====")

    histories = []
    labels = []

    # Create 5 users (mixed types)
    for i in range(5):

        if i % 2 == 0:
            user, label = generate_user(decay=True)
        else:
            user, label = generate_user(decay=False)

        histories.append(user)
        labels.append(label)

    # Split (3 train, 2 test automatically)
    X_train, X_test, y_train, y_test = train_test_split(
        histories, labels, test_size=0.4, random_state=42
    )

    system = SkillPulseSystem()
    system.train(X_train, y_train)

    test_features = np.array(
        [system.extractor.extract(h) for h in X_test]
    )

    accuracy = system.classifier.evaluate(test_features, y_test)

    print("Model Accuracy:", accuracy)

    print("\n--- Individual User Predictions ---")

    for i, user in enumerate(histories):
        status = system.analyze(user)
        print(f"User {i+1}: {status}")

if __name__ == "__main__":
    main()