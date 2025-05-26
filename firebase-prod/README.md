
# Copying Test Indexes and Rules in Firestore to Prod Account

- `firebase init firestore` in test folder
- Overwrite `firestore.indexes.json` and `firestore.rules` files.
- Do the same in prod folder.
- Overwrite the file in prod folder with the file in test folder
  - `cp firestore.indexes.json ../firebase-prod/firestore.indexes.json`
  - `cp firestore.rules ../firebase-prod/firestore.rules`
- In prod folder, run `firebase deploy`