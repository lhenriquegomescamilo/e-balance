import { gql } from 'apollo-angular';

export const TRANSACTIONS_QUERY = gql`
  query Transactions($filter: TransactionFilterInput) {
    transactions(filter: $filter) {
      id
      operatedAt
      description
      value
      balance
      category {
        id
        name
      }
    }
  }
`;
