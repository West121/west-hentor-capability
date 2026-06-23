import assert from 'node:assert/strict';
import {
  demoCountriesQuery,
  demoSendAndGetDateQuery,
  demoSendAndGetDateRangeQuery,
  demoSendAndGetDateTimeQuery,
  demoSendAndGetValueQuery,
} from '../src/services/requestContracts.ts';

assert.equal(
  demoSendAndGetDateQuery(new Date('2099-01-02T03:04:05.000Z')),
  '/api/services/app/DemoUiComponents/SendAndGetDate?date=2099-01-02T03%3A04%3A05.000Z',
);

assert.equal(
  demoSendAndGetDateTimeQuery('2099-01-02T03:04:05.000Z'),
  '/api/services/app/DemoUiComponents/SendAndGetDateTime?date=2099-01-02T03%3A04%3A05.000Z',
);

assert.equal(
  demoSendAndGetDateRangeQuery({
    startDate: '2099-01-01T00:00:00.000Z',
    endDate: '2099-01-03T00:00:00.000Z',
  }),
  '/api/services/app/DemoUiComponents/SendAndGetDateRange?startDate=2099-01-01T00%3A00%3A00.000Z&endDate=2099-01-03T00%3A00%3A00.000Z',
);

assert.equal(
  demoCountriesQuery('United'),
  '/api/services/app/DemoUiComponents/GetCountries?searchTerm=United',
);

assert.equal(
  demoSendAndGetValueQuery('copied value'),
  '/api/services/app/DemoUiComponents/SendAndGetValue?input=copied+value',
);
