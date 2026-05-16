# Assessment Notes

## Trade Execution Assumptions

- `POST /api/trades/execute` executes market orders only.
- Request body must include `userId`, `pairName`, `tradeType`, and `quantity`.
- The client does not provide price. The system uses the latest stored best price.
- `BUY` trades execute at the latest ask price.
- `SELL` trades execute at the latest bid price.
- The quote currency is debited for buys, and the base currency is debited for sells.
- Wallet credits create a wallet row if the user does not already hold that symbol.
- Wallet debits are atomic and require sufficient balance in the database update.
- Trade execution is transactional: wallet changes and trade audit insertion commit or roll back together.

## Price Scheduler Fix

- The price scheduler originally saved bid and ask prices in reverse order.
- The price scheduler also saved prices against the opposite trading pair, for example `BTCUSDT` prices were stored under `ETHUSDT`.
- This matters because the trade execution API uses the latest stored price to calculate trades.
- The scheduler now stores each fetched price under the correct pair and keeps bid/ask semantics correct:
  - `bidPrice` is the price used for `SELL`.
  - `askPrice` is the price used for `BUY`.

## Manual Test Evidence

Note: These manual Swagger test results were captured before the price scheduler fix above. The tests still prove the trade execution and wallet update behavior, but the displayed `ETHUSDT` price values reflect the pre-fix scheduler data.

### Successful BUY

User `1` wallet before:

```json
[
  {
    "symbol": "USDT",
    "name": "Tether",
    "balance": 10000
  }
]
```

Request:

```json
{
  "userId": 1,
  "pairName": "ETHUSDT",
  "tradeType": "BUY",
  "quantity": 0.1
}
```

Response:

```json
{
  "tradeId": 80,
  "userId": 1,
  "pairName": "ETHUSDT",
  "tradeType": "BUY",
  "quantity": 0.1,
  "price": 46070.4,
  "totalAmount": 4607.04,
  "priceSource": "BINANCE",
  "tradeTime": "2026-05-16T14:54:25.3695622"
}
```

User `1` wallet after:

```json
[
  {
    "symbol": "ETH",
    "name": "Ethereum",
    "balance": 0.1
  },
  {
    "symbol": "USDT",
    "name": "Tether",
    "balance": 5392.96
  }
]
```

Result: `USDT` decreased by `4607.04`, and `ETH` increased by `0.1`.

### Successful SELL

User `4` wallet before:

```json
[
  {
    "symbol": "ETH",
    "name": "Ethereum",
    "balance": 5
  },
  {
    "symbol": "USDT",
    "name": "Tether",
    "balance": 15000
  }
]
```

Request:

```json
{
  "userId": 4,
  "pairName": "ETHUSDT",
  "tradeType": "SELL",
  "quantity": 1
}
```

Response:

```json
{
  "tradeId": 81,
  "userId": 4,
  "pairName": "ETHUSDT",
  "tradeType": "SELL",
  "quantity": 1,
  "price": 46080.4,
  "totalAmount": 46080.4,
  "priceSource": "BINANCE",
  "tradeTime": "2026-05-16T14:59:13.2630395"
}
```

User `4` wallet after:

```json
[
  {
    "symbol": "ETH",
    "name": "Ethereum",
    "balance": 4
  },
  {
    "symbol": "USDT",
    "name": "Tether",
    "balance": 61080.4
  }
]
```

Result: `ETH` decreased by `1`, and `USDT` increased by `46080.4`.

### Invalid Pair

Request:

```json
{
  "userId": 1,
  "pairName": "DOGEUSDT",
  "tradeType": "BUY",
  "quantity": 1
}
```

Response:

```json
{
  "timestamp": "2026-05-16T15:02:32.5235249",
  "status": 400,
  "error": "Bad Request",
  "message": "Unsupported or inactive trading pair: DOGEUSDT",
  "path": "/api/trades/execute"
}
```

Result: unsupported trading pair was rejected.

### Invalid Quantity

Request:

```json
{
  "userId": 1,
  "pairName": "ETHUSDT",
  "tradeType": "BUY",
  "quantity": 0
}
```

Response:

```json
{
  "timestamp": "2026-05-16T15:05:28.4896183",
  "status": 400,
  "error": "Bad Request",
  "message": "quantity must be greater than zero",
  "path": "/api/trades/execute"
}
```

Result: zero quantity was rejected.

### Insufficient Balance For BUY

Request:

```json
{
  "userId": 1,
  "pairName": "ETHUSDT",
  "tradeType": "BUY",
  "quantity": 999
}
```

Response:

```json
{
  "timestamp": "2026-05-16T15:07:23.9660242",
  "status": 400,
  "error": "Bad Request",
  "message": "Insufficient USDT balance",
  "path": "/api/trades/execute"
}
```

Result: user could not buy more than their available `USDT` balance.

### Insufficient Balance For SELL

Request:

```json
{
  "userId": 1,
  "pairName": "ETHUSDT",
  "tradeType": "SELL",
  "quantity": 5
}
```

Response:

```json
{
  "timestamp": "2026-05-16T15:09:20.8123637",
  "status": 400,
  "error": "Bad Request",
  "message": "Insufficient ETH balance",
  "path": "/api/trades/execute"
}
```

Result: user could not sell more than their available `ETH` balance.

## Automated Test Evidence

Command:

```powershell
.\mvnw.cmd test
```

Result:

```text
Tests run: 10, Failures: 0, Errors: 0
BUILD SUCCESS
```
