import pandas as pd

df = pd.read_csv("/Users/pablo/Documents/java-projects/unip-tcc/fraud_oracle.csv")

print(df.columns.tolist())
print(df.head())