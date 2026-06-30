# FinTrack - Personal Financial Tracker

A comprehensive web-based financial tracking application built with Flask and SQLite for managing income, expenses, and analyzing spending patterns across multiple categories.

## Features

✅ **Category Management**
- Create, edit, and delete expense/income categories
- Color-coded categories for easy identification
- Icon support for visual organization

✅ **Transaction Tracking**
- Add income and expense transactions
- Categorize each transaction
- Track transaction dates and descriptions
- Edit and delete transactions

✅ **Dashboard Analytics**
- Total income, expense, and balance summary
- Expense breakdown by category (pie chart)
- Monthly income/expense trends (line chart)
- Recent transactions overview
- Quick statistics

✅ **Responsive Web Interface**
- Mobile-friendly design
- Bootstrap-based UI
- Real-time data updates
- Intuitive navigation

## Tech Stack

- **Backend**: Flask 3.0.0
- **Database**: SQLite
- **Frontend**: HTML5, CSS3, JavaScript (ES6+)
- **UI Framework**: Bootstrap 5
- **Charts**: Chart.js
- **Icons**: Font Awesome
- **Server**: Flask Development Server

## Project Structure

```
fintrack-app/
├── app.py                          # Main Flask application
├── requirements.txt                # Python dependencies
├── .env                            # Environment variables
├── fintrack.db                     # SQLite database (auto-created)
├── templates/                      # HTML templates
│   ├── base.html                  # Base template
│   ├── index.html                 # Home page
│   ├── dashboard.html             # Dashboard page
│   └── transactions.html          # Transactions page
└── static/                         # Static files
    ├── css/
    │   └── style.css              # Custom styles
    └── js/
        └── api.js                 # API helper functions
```

## Installation & Setup

### Prerequisites
- Python 3.8 or higher
- pip (Python package manager)

### Step 1: Install Dependencies

```bash
cd fintrack-app
pip install -r requirements.txt
```

### Step 2: Run the Application

```bash
python app.py
```

The application will start on `http://localhost:5000`

## API Endpoints

### Categories
- `GET /api/categories` - Get all categories
- `POST /api/categories` - Create new category
- `GET /api/categories/<id>` - Get category details
- `PUT /api/categories/<id>` - Update category
- `DELETE /api/categories/<id>` - Delete category

### Transactions
- `GET /api/transactions` - Get all transactions
- `POST /api/transactions` - Create new transaction
- `GET /api/transactions/<id>` - Get transaction details
- `PUT /api/transactions/<id>` - Update transaction
- `DELETE /api/transactions/<id>` - Delete transaction

**Query Parameters for Transactions:**
- `category_id` - Filter by category
- `type` - Filter by type (income/expense)
- `limit` - Limit number of results (default: 100)

### Analytics
- `GET /api/analytics/summary` - Get financial summary (income, expense, balance)
- `GET /api/analytics/category-breakdown` - Get expense breakdown by category
- `GET /api/analytics/monthly` - Get monthly income/expense trends

## Usage Guide

### Adding Your First Transaction

1. Visit `http://localhost:5000/transactions`
2. Click "Manage Categories" and create a new category (e.g., "Groceries")
3. Fill in the transaction details:
   - Description: What the transaction is for
   - Amount: How much (in dollars)
   - Category: Select the category
   - Type: Choose "Income" or "Expense"
   - Date: When the transaction occurred
4. Click "Add Transaction"

### Viewing Analytics

1. Go to the Dashboard: `http://localhost:5000/dashboard`
2. View your financial summary in the cards at the top
3. Analyze spending by category in the pie chart
4. Track monthly trends in the line chart
5. See recent transactions in the table

### Managing Categories

1. In the Transactions page, scroll to "Manage Categories"
2. Enter a category name and choose a color
3. Click "Create Category"
4. Delete categories by clicking the × on existing category badges

## Database Schema

### Category Table
```sql
CREATE TABLE category (
    id INTEGER PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    description VARCHAR(255),
    color VARCHAR(7) DEFAULT '#3498db',
    icon VARCHAR(50) DEFAULT 'tag'
)
```

### Transaction Table
```sql
CREATE TABLE transaction (
    id INTEGER PRIMARY KEY,
    amount FLOAT NOT NULL,
    description VARCHAR(255) NOT NULL,
    category_id INTEGER NOT NULL,
    transaction_type VARCHAR(10) NOT NULL,
    date DATETIME NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (category_id) REFERENCES category(id)
)
```

## Environment Variables

Create a `.env` file in the project root:

```env
FLASK_ENV=development
FLASK_APP=app.py
DEBUG=True
```

For production:
```env
FLASK_ENV=production
DEBUG=False
```

## Running on Different Ports

To run on a different port, modify the `app.run()` call in `app.py`:

```python
app.run(debug=True, host='0.0.0.0', port=8000)  # Change 8000 to your desired port
```

Or set via environment variable:
```bash
set FLASK_PORT=8000  # Windows
flask run
```

## Troubleshooting

### Dependencies not installing
```bash
pip install --upgrade pip
pip install -r requirements.txt
```

### Port already in use
```bash
# Windows: Find and kill process on port 5000
netstat -ano | findstr :5000
taskkill /PID <PID> /F

# Linux/Mac:
lsof -i :5000
kill -9 <PID>
```

### Database errors
Delete `fintrack.db` and restart the app to reset the database:
```bash
del fintrack.db
python app.py
```

## Development Tips

### Access Flask Shell
```bash
python
>>> from app import app, db, Category, Transaction
>>> with app.app_context():
...     categories = Category.query.all()
```

### Reset Database
```python
from app import app, db
with app.app_context():
    db.drop_all()
    db.create_all()
```

## Roadmap

- [ ] User authentication and multi-user support
- [ ] Import/export transactions (CSV, JSON)
- [ ] Budget setting and tracking
- [ ] Recurring transactions
- [ ] Advanced filtering and search
- [ ] Mobile app (React Native)
- [ ] Cloud synchronization
- [ ] Multi-currency support
- [ ] OCR receipt scanning

## Contributing

Contributions are welcome! Please feel free to submit issues and pull requests.

## License

This project is open source and available under the MIT License.

## Support

For issues, questions, or suggestions, please open an issue on the project repository.

---

**FinTrack** - Track your finances, understand your spending, plan your future.
