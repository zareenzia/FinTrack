# FinTrack - Installation & Setup Instructions

## Complete Setup Guide

### System Requirements
- Python 3.8 or higher
- pip package manager
- 100MB free disk space
- Modern web browser (Chrome, Firefox, Edge, Safari)

### Installation Steps

#### Step 1: Navigate to Project Directory
```bash
cd d:\finzin\fintrack-app
```

#### Step 2: Create Virtual Environment (Optional but Recommended)
```bash
# Create virtual environment
python -m venv venv

# Activate virtual environment
# On Windows:
venv\Scripts\activate

# On Linux/Mac:
source venv/bin/activate
```

#### Step 3: Install Dependencies
```bash
pip install -r requirements.txt
```

Expected output: "Successfully installed Flask-3.0.0 Flask-CORS-4.0.0 Flask-SQLAlchemy-3.1.1 ..."

#### Step 4: Run the Application
```bash
python app.py
```

You should see:
```
* Running on http://127.0.0.1:5000
* Debugger is active!
```

#### Step 5: Open in Browser
- Local: http://localhost:5000
- Network: http://192.168.x.x:5000 (from other devices)

---

## Features Breakdown

### 1. Home Page (/)
- Welcome message
- Feature overview
- Quick links to Dashboard and Transactions

### 2. Dashboard (/dashboard)
- **Summary Cards:**
  - Total Income (green)
  - Total Expenses (red)
  - Net Balance (blue)
  - Transaction Count (yellow)

- **Charts:**
  - Expense breakdown by category (doughnut chart)
  - Monthly income/expense trends (line chart)

- **Recent Transactions Table:**
  - Date, description, category, type, amount
  - Shows last 10 transactions

### 3. Transactions (/transactions)
- **Left Panel - Forms:**
  - Add Transaction form
  - Manage Categories form
  - Category list with badges

- **Right Panel - Transaction List:**
  - All transactions with delete options
  - Filterable and sortable

---

## Package Dependencies

| Package | Version | Purpose |
|---------|---------|---------|
| Flask | 3.0.0 | Web framework |
| Flask-CORS | 4.0.0 | Cross-origin requests |
| Flask-SQLAlchemy | 3.1.1 | ORM for database |
| SQLAlchemy | 2.0+ | Database toolkit |
| python-dotenv | 1.0.0 | Environment variables |
| Werkzeug | 3.0.1 | WSGI utilities |
| Jinja2 | 3.1.6 | Template engine |

---

## Database

### Automatic Initialization
- Database created on first run
- Located at: `instance/fintrack.db`
- SQLite format

### Database Tables

#### Categories Table
```
Columns:
- id (Primary Key)
- name (Unique, 100 chars)
- description (255 chars)
- color (7 chars - hex color)
- icon (50 chars)
- transactions (relationship)
```

#### Transactions Table
```
Columns:
- id (Primary Key)
- amount (Float)
- description (255 chars)
- category_id (Foreign Key)
- transaction_type (income/expense)
- date (DateTime)
- created_at (DateTime)
```

---

## Configuration

### Environment Variables (.env)
```
FLASK_ENV=development      # Set to 'production' for live
FLASK_APP=app.py          # Main application file
DEBUG=True                # Set to 'False' for production
```

### For Production
1. Set `FLASK_ENV=production`
2. Set `DEBUG=False`
3. Use production server (Gunicorn, uWSGI)
4. Set up database backups
5. Configure HTTPS

---

## Running on Different Ports

### Method 1: Edit app.py
```python
if __name__ == '__main__':
    with app.app_context():
        db.create_all()
    app.run(debug=True, host='0.0.0.0', port=8080)  # Change port here
```

### Method 2: Environment Variable
```bash
set FLASK_PORT=8080
python app.py
```

### Method 3: Command Line
```bash
flask run --port 8080
```

---

## Data Import/Export

### Export Transactions to CSV
```python
from app import app, Transaction

with app.app_context():
    transactions = Transaction.query.all()
    with open('transactions.csv', 'w') as f:
        f.write('Date,Description,Category,Type,Amount\n')
        for tx in transactions:
            f.write(f'{tx.date},{tx.description},{tx.category.name},{tx.transaction_type},{tx.amount}\n')
```

### Reset Database
```bash
# Delete the database file
del instance/fintrack.db

# Restart application
python app.py
```

---

## Backup & Restore

### Backup Database
```bash
# Copy database file
copy instance/fintrack.db instance/fintrack_backup.db
```

### Restore Database
```bash
# Replace current database with backup
copy instance/fintrack_backup.db instance/fintrack.db
```

---

## Performance Tips

1. **Use Virtual Environment** - Isolates dependencies
2. **Limit Query Results** - Use `limit` parameter in API
3. **Index Frequently Queried Fields** - Category and date
4. **Archive Old Transactions** - Keep database lean
5. **Use Production WSGI Server** - Better performance than Flask dev server

---

## Deployment Options

### Option 1: Heroku
```bash
heroku login
heroku create your-app-name
git push heroku main
```

### Option 2: PythonAnywhere
- Sign up at pythonanywhere.com
- Upload files
- Configure web app
- Enable WSGI

### Option 3: Docker
```dockerfile
FROM python:3.9
WORKDIR /app
COPY requirements.txt .
RUN pip install -r requirements.txt
COPY . .
CMD ["python", "app.py"]
```

### Option 4: AWS/Azure/GCP
- Use app platform services
- Configure environment variables
- Set up database
- Enable monitoring

---

## API Usage Examples

### Add a Category
```bash
curl -X POST http://localhost:5000/api/categories \
  -H "Content-Type: application/json" \
  -d '{"name":"Groceries","color":"#27ae60"}'
```

### Add a Transaction
```bash
curl -X POST http://localhost:5000/api/transactions \
  -H "Content-Type: application/json" \
  -d '{
    "amount":50.00,
    "description":"Grocery shopping",
    "category_id":1,
    "transaction_type":"expense",
    "date":"2024-06-30"
  }'
```

### Get Analytics
```bash
curl http://localhost:5000/api/analytics/summary
```

---

## Troubleshooting

| Issue | Solution |
|-------|----------|
| `ModuleNotFoundError: No module named 'flask'` | Run `pip install -r requirements.txt` |
| `Port 5000 already in use` | Change port or kill process using port |
| `Database locked error` | Restart Flask app |
| `Static files not loading` | Check `static/` folder exists |
| `Template not found` | Check `templates/` folder exists |

---

## Next Steps

1. ✅ Install and run FinTrack
2. 📊 Add your first transactions
3. 🏷️ Create categories for organization
4. 📈 Review dashboard analytics
5. 🔄 Plan for integration with banking APIs
6. 🚀 Consider production deployment

---

## Support Resources

- Documentation: See `README.md`
- Quick Start: See `QUICKSTART.md`
- Flask Docs: https://flask.palletsprojects.com/
- SQLAlchemy Docs: https://docs.sqlalchemy.org/
- Bootstrap Docs: https://getbootstrap.com/

---

**FinTrack is ready to use! Start tracking your finances today.** 💰
