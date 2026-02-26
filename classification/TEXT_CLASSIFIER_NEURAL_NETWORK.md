# TextClassifierNeuralNetwork — Mathematical Reference

A shallow feed-forward neural network that classifies financial transaction descriptions
(e.g. *"Starbucks 56413 Cc Mar"*) into spending categories (e.g. *RESTAURANTE*).
The pipeline has three stages: **tokenisation → encoding → neural classification**.

---

## 1. Tokenisation and Vocabulary

### 1.1 Vocabulary Construction

Given a training corpus of *N* labelled examples

```
LABEL₁ ; Description₁
LABEL₂ ; Description₂
   ⋮
LABELₙ ; Descriptionₙ
```

every description is lowercased and split on whitespace runs. The **vocabulary** *V* is
the ordered, deduplicated list of all resulting tokens across the full corpus:

$$V = \bigl[ w_1,\; w_2,\; \ldots,\; w_{|V|} \bigr], \quad |V| = n_{\text{input}}$$

Blank tokens produced by consecutive whitespace (e.g. `"Trf.Imed.   de"`) are dropped.

**Example — dataset fragment:**

| Raw line | Tokens |
|---|---|
| `RESTAURANTE;Sabores De Viana` | `["sabores", "de", "viana"]` |
| `VIA_VERDE;Via Verde Auto-Estrada` | `["via", "verde", "auto-estrada"]` |
| `INVESTIMENTO;Trade Republic` | `["trade", "republic"]` |

Assuming these three lines form the entire corpus, the vocabulary is:

$$V = [\text{sabores},\; \text{de},\; \text{viana},\; \text{via},\; \text{verde},\; \text{auto-estrada},\; \text{trade},\; \text{republic}]$$

### 1.2 Label Set

The **label set** *C* is the ordered, deduplicated list of category names in corpus order:

$$C = [c_1, c_2, \ldots, c_K], \quad K = n_{\text{output}}$$

### 1.3 Bag-of-Words Encoding

A description $d$ is encoded as a binary vector $\mathbf{x} \in \{0,1\}^{|V|}$ where each
component indicates **exact word membership** (not substring containment):

$$x_i = \begin{cases} 1 & \text{if } w_i \in \text{tokens}(d) \\ 0 & \text{otherwise} \end{cases}$$

where $\text{tokens}(d) = \{\,t \mid t \in d.\text{lowercase}().\text{split}(\backslash\text{s+})\,\}$.

**Example — encoding `"Via Verde"`:**

| Index | Token | In description? | $x_i$ |
|---|---|---|---|
| 0 | sabores | no | 0 |
| 1 | de | no | 0 |
| 2 | viana | no | 0 |
| 3 | via | **yes** | **1** |
| 4 | verde | **yes** | **1** |
| 5 | auto-estrada | no | 0 |
| 6 | trade | no | 0 |
| 7 | republic | no | 0 |

$$\mathbf{x} = [0,\; 0,\; 0,\; 1,\; 1,\; 0,\; 0,\; 0]$$

### 1.4 One-Hot Target Encoding

The correct class $c$ is encoded as a one-hot vector $\mathbf{y} \in \{0,1\}^K$:

$$y_k = \begin{cases} 1 & \text{if } c_k = c \\ 0 & \text{otherwise} \end{cases}$$

---

## 2. Network Architecture

The network is a **3-layer feed-forward network**:

$$\underbrace{\mathbf{x}}_{\text{input}} \;\longrightarrow\; \underbrace{\mathbf{h}}_{\text{hidden}} \;\longrightarrow\; \underbrace{\hat{\mathbf{y}}}_{\text{output}}$$

| Layer | Dimension | Activation |
|---|---|---|
| Input | $n_{\text{input}} = \|V\|$ | — (identity) |
| Hidden | $H = n_{\text{hidden}}$ | Sigmoid $\sigma$ |
| Output | $K = \|C\|$ | Softmax |

### Parameters

| Symbol | Shape | Initialisation | Description |
|---|---|---|---|
| $W^{IH}$ | $\|V\| \times H$ | $\mathcal{N}(0,\,0.1^2)$ | Input → Hidden weights |
| $W^{HO}$ | $H \times K$ | $\mathcal{N}(0,\,0.1^2)$ | Hidden → Output weights |
| $\mathbf{b}^H$ | $H$ | $\mathbf{0}$ | Hidden biases |
| $\mathbf{b}^O$ | $K$ | $\mathbf{0}$ | Output biases |

---

## 3. Forward Pass (Inference)

### 3.1 Hidden Layer

Each hidden neuron $j$ computes a weighted sum of all inputs, offset by a bias, then
squashes the result through the **sigmoid** function:

$$h_j = \sigma\!\left( b^H_j + \sum_{i=1}^{|V|} x_i \cdot W^{IH}_{ij} \right), \quad j = 1,\ldots,H$$

where:

$$\sigma(z) = \frac{1}{1 + e^{-z}} \in (0, 1)$$

Because the input $\mathbf{x}$ is binary, the sum collapses to a sum over **active tokens only**:

$$b^H_j + \sum_{i:\, x_i = 1} W^{IH}_{ij}$$

### 3.2 Output Logits

Each output neuron $k$ computes a linear combination of the hidden activations:

$$z_k = b^O_k + \sum_{j=1}^{H} h_j \cdot W^{HO}_{jk}, \quad k = 1,\ldots,K$$

### 3.3 Softmax Output (Probability Distribution)

The logit vector $\mathbf{z} \in \mathbb{R}^K$ is converted to a probability distribution
via **softmax** (numerically stabilised by subtracting the maximum logit):

$$\hat{y}_k = \text{softmax}(\mathbf{z})_k = \frac{e^{z_k - z_{\max}}}{\displaystyle\sum_{\ell=1}^{K} e^{z_\ell - z_{\max}}}, \quad z_{\max} = \max_k z_k$$

Properties:

$$0 < \hat{y}_k < 1, \qquad \sum_{k=1}^{K} \hat{y}_k = 1$$

The output vector $\hat{\mathbf{y}}$ is a **proper probability distribution** over all categories.
Unlike sigmoid, softmax forces the classes to compete: raising one class's probability
necessarily lowers the others.

---

## 4. Classification Decision

Given the probability vector $\hat{\mathbf{y}}$ produced by the forward pass, the predicted
category is the one with the highest probability:

$$\hat{c} = C\!\left[\;\arg\max_{k}\; \hat{y}_k\;\right]$$

The **confidence** of the prediction is the winning probability itself: $\hat{y}_{\hat{k}}$.

**Example** — for $K = 4$ classes after encoding `"Via Verde"`:

$$\hat{\mathbf{y}} = [0.01,\; \mathbf{0.91},\; 0.05,\; 0.03] \implies \hat{c} = C[1] = \texttt{VIA\_VERDE}$$

with confidence $0.91$.

---

## 5. Training

### 5.1 Loss Function — Cross-Entropy

For a single training example with true one-hot label $\mathbf{y}$ and prediction $\hat{\mathbf{y}}$,
the **categorical cross-entropy loss** is:

$$\mathcal{L} = -\sum_{k=1}^{K} y_k \log \hat{y}_k$$

Because $\mathbf{y}$ is one-hot (only class $c$ is 1), this simplifies to:

$$\mathcal{L} = -\log \hat{y}_c$$

Minimising $\mathcal{L}$ maximises the predicted probability of the correct class.

### 5.2 Class Balancing Weight

Training examples are weighted by the **inverse-frequency class weight** $w_c$ to compensate
for class imbalance (e.g. RESTAURANTE having 39 entries vs. 1 for rarer categories):

$$w_c = \frac{\bar{n}}{n_c}, \qquad \bar{n} = \frac{N}{K}$$

where $N$ is the total number of training examples, $K$ the number of classes, and $n_c$ the
count of training examples for class $c$. The effective loss becomes:

$$\mathcal{L}^{(w)} = w_c \cdot \mathcal{L} = -w_c \log \hat{y}_c$$

### 5.3 Backpropagation — Output Layer

For **softmax + cross-entropy**, the gradient of the loss with respect to each pre-softmax
logit $z_k$ simplifies to:

$$\frac{\partial \mathcal{L}}{\partial z_k} = \hat{y}_k - y_k$$

With the class weight $w_c$ applied, the **output delta** (negative gradient, sign-flipped
for the weight-update convention used in the code) is:

$$\delta^O_k = \bigl(y_k - \hat{y}_k\bigr)\cdot w_c$$

This has an elegant interpretation: for the correct class $c$, the delta is positive
(predicted probability too low → increase it); for every wrong class it is negative.

### 5.4 Backpropagation — Hidden Layer

The error signal is propagated back through $W^{HO}$ and modulated by the sigmoid derivative:

$$\sigma'(h_j) = h_j \,(1 - h_j)$$

The **hidden delta** for neuron $j$ is:

$$\delta^H_j = \left(\sum_{k=1}^{K} \delta^O_k \cdot W^{HO}_{jk}\right) \cdot \sigma'(h_j)$$

### 5.5 Weight Updates (Gradient Ascent on Negative Loss)

Parameters are updated by **gradient descent**, optionally with **L1 (Lasso) regularisation**
on the weights (not the biases):

**Hidden → Output weights:**

$$W^{HO}_{jk} \;\leftarrow\; W^{HO}_{jk} + \eta\,\delta^O_k\, h_j \;-\; \eta\,\lambda\,\text{sign}(W^{HO}_{jk})$$

**Output biases:**

$$b^O_k \;\leftarrow\; b^O_k + \eta\,\delta^O_k$$

**Input → Hidden weights:**

$$W^{IH}_{ij} \;\leftarrow\; W^{IH}_{ij} + \eta\,\delta^H_j\, x_i \;-\; \eta\,\lambda\,\text{sign}(W^{IH}_{ij})$$

**Hidden biases:**

$$b^H_j \;\leftarrow\; b^H_j + \eta\,\delta^H_j$$

where $\eta$ is the learning rate and $\lambda$ is the L1 regularisation strength.

> **Important:** For sparse bag-of-words inputs, the L1 term must be set to $\lambda = 0$.
> Because L1 is applied at every training step (even when $x_i = 0$), a non-zero $\lambda$
> causes the input→hidden weights for rare words to be driven to zero before the gradient
> signal has time to overcome the shrinkage, making the network ignore all word features.

### 5.6 Training Loop

For $E$ epochs over the full corpus:

$$\text{for epoch } e = 1,\ldots,E:\quad\text{for each } (\mathbf{x}^{(n)}, \mathbf{y}^{(n)}) \in \text{corpus}:\quad\text{forward} \to \text{backward} \to \text{update}$$

---

## 6. Full Forward-Pass Diagram

```
Description: "Via Verde"
       │
       ▼  lowercase + split on \s+
  tokens: {"via", "verde"}
       │
       ▼  encode against vocabulary V (size |V|)
  x = [0, 0, 0, 1, 1, 0, 0, ...]      ∈ {0,1}^|V|
       │
       │   W^IH  (|V| × H)
       ▼
  z^H_j = b^H_j + Σᵢ xᵢ · W^IH_ij    ∈ R^H
       │
       ▼  σ(·) elementwise
  h   = σ(z^H)                         ∈ (0,1)^H
       │
       │   W^HO  (H × K)
       ▼
  z^O_k = b^O_k + Σⱼ hⱼ · W^HO_jk    ∈ R^K   (logits)
       │
       ▼  softmax
  ŷ   = softmax(z^O)                   ∈ (0,1)^K,  Σŷ=1
       │
       ▼  argmax
  ĉ   = C[argmax_k ŷ_k]               predicted category
```

---

## 7. Model Evaluation — R² Score

After training, the model is evaluated using the **coefficient of determination** R²,
applied over all test samples and all output dimensions simultaneously.

Let $y^{(n)}_k$ be the true one-hot value and $\hat{y}^{(n)}_k$ be the predicted probability
for sample $n$ and class $k$. Define:

$$\bar{y}_k = \frac{1}{N}\sum_{n=1}^{N} y^{(n)}_k$$

$$\text{SS}_{\text{res}} = \sum_{n=1}^{N}\sum_{k=1}^{K}\bigl(y^{(n)}_k - \hat{y}^{(n)}_k\bigr)^2$$

$$\text{SS}_{\text{tot}} = \sum_{n=1}^{N}\sum_{k=1}^{K}\bigl(y^{(n)}_k - \bar{y}_k\bigr)^2$$

$$R^2 = 1 - \frac{\text{SS}_{\text{res}}}{\text{SS}_{\text{tot}}}$$

| R² range | Interpretation |
|---|---|
| $R^2 = 1.0$ | Perfect predictions on every sample |
| $R^2 = 0.0$ | Predictions no better than predicting the class-frequency mean |
| $R^2 < 0$ | Predictions worse than a naïve frequency baseline |

> R² is reported on the **training set** at the end of training as a convergence indicator.
> A healthy model should achieve R² > 0.9 on training data after 500 epochs.

---

## 8. Hyperparameters Summary

| Parameter | Symbol | Production value | Notes |
|---|---|---|---|
| Vocabulary size | $\|V\|$ | ~270 (dataset-dependent) | Built from training corpus |
| Hidden units | $H$ | 256 | Set in `TrainCommand` |
| Output classes | $K$ | 47 | Number of distinct labels |
| Learning rate | $\eta$ | 0.1 | |
| L1 coefficient | $\lambda$ | **0.0** | Must be 0 for bag-of-words |
| Training epochs | $E$ | 500 | |
| Class weight | $w_c$ | $\bar{n}/n_c$ | Inverse frequency |

---

## 9. Worked Example End-to-End

**Training entry:** `COMBUSTIVEL;Repsol E0394`

**Step 1 — Tokenise:**

$$\text{tokens}(\text{"Repsol E0394"}) = \{\text{repsol},\; \text{e0394}\}$$

**Step 2 — Encode** (assuming `repsol` is at index 7 and `e0394` at index 8 in $V$):

$$\mathbf{x} = [\underbrace{0,\ldots,0}_{7},\; 1,\; 1,\; \underbrace{0,\ldots,0}_{|V|-9}]$$

**Step 3 — One-hot target** (assuming COMBUSTIVEL is class index 1 of 47):

$$\mathbf{y} = [0,\; 1,\; 0,\; \ldots,\; 0]$$

**Step 4 — Forward pass:**

$$h_j = \sigma\!\left(b^H_j + W^{IH}_{7j} + W^{IH}_{8j}\right) \;\approx\; \sigma(0 + 0 + 0) = 0.5 \quad \text{(at initialisation)}$$

$$\hat{y}_k = \text{softmax}(\mathbf{z})_k \approx \frac{1}{47} \approx 0.021 \quad \text{(at initialisation)}$$

**Step 5 — Output delta for COMBUSTIVEL** (class weight $w_1 = \bar{n}/n_1 \approx 4.23/4 \approx 1.06$):

$$\delta^O_1 = (y_1 - \hat{y}_1)\cdot w_1 = (1 - 0.021)\cdot 1.06 \approx +1.04 \quad \text{(push up)}$$

$$\delta^O_{k\neq1} = (0 - 0.021)\cdot 1.06 \approx -0.022 \quad \text{(push down)}$$

**Step 6 — Weight update** for $W^{HO}_{j,\text{COMBUSTIVEL}}$:

$$W^{HO}_{j1} \leftarrow W^{HO}_{j1} + 0.1 \times 1.04 \times 0.5 = W^{HO}_{j1} + 0.052$$

After many epochs, $W^{HO}_{\cdot,\text{COMBUSTIVEL}}$ grows, the hidden gradients become
meaningful, and $W^{IH}_{7\cdot}$, $W^{IH}_{8\cdot}$ (for `repsol`, `e0394`) are updated
so that these tokens activate hidden neurons that strongly excite the COMBUSTIVEL output.

**Step 7 — Prediction on `"Repsol E0394"` after training:**

$$\hat{\mathbf{y}} \approx [\ldots,\; \underbrace{0.94}_{\text{COMBUSTIVEL}},\; \ldots] \implies \hat{c} = \text{COMBUSTIVEL}$$
