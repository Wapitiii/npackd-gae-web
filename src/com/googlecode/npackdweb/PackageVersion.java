package com.googlecode.npackdweb;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.persistence.Id;
import javax.persistence.PostLoad;
import javax.persistence.PrePersist;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.google.appengine.api.datastore.Text;
import com.googlecode.objectify.Key;
import com.googlecode.objectify.annotation.AlsoLoad;
import com.googlecode.objectify.annotation.Cached;
import com.googlecode.objectify.annotation.Entity;

/**
 * A package version.
 */
@Entity
@Cached
public class PackageVersion {
	private List<Object> fileContents = new ArrayList<Object>();

	@Id
	public String name = "";
	@AlsoLoad("package")
	public String package_ = "";
	public String version = "";
	public boolean oneFile;
	public String url = "";
	public String sha1 = "";
	public String detectMSI = "";
	public List<String> importantFileTitles = new ArrayList<String>();
	public List<String> importantFilePaths = new ArrayList<String>();
	public List<String> filePaths = new ArrayList<String>();
	public List<String> dependencyPackages = new ArrayList<String>();
	public List<String> dependencyVersionRanges = new ArrayList<String>();
	public List<String> dependencyEnvVars = new ArrayList<String>();
	public List<String> detectFilePaths = new ArrayList<String>();
	public List<String> detectFileSHA1s = new ArrayList<String>();
	public List<String> tags = new ArrayList<String>();

	/** can be null if the check was not yet performed */
	public Date downloadCheckAt;

	/** error message or null if none */
	public String downloadCheckError;

	/**
	 * For Objectify.
	 */
	public PackageVersion() {
	}

	/**
	 * @param package_
	 *            full internal package name
	 * @param version
	 *            version number
	 */
	public PackageVersion(String package_, String version) {
		this.name = package_ + "@" + version;
		this.package_ = package_;
		this.version = version;
	}

	public String getName() {
		return name;
	}

	public String getVersion() {
		return version;
	}

	public String getPackage() {
		return package_;
	}

	public boolean getOneFile() {
		return oneFile;
	}

	public String getUrl() {
		return url;
	}

	public String getSha1() {
		return sha1;
	}

	public String getDetectMSI() {
		return detectMSI;
	}

	public List<String> getDependencyPackages() {
		return dependencyPackages;
	}

	public List<String> getDependencyVersionRanges() {
		return dependencyVersionRanges;
	}

	/**
	 * @return copy of this object
	 */
	public PackageVersion copy() {
		PackageVersion c = new PackageVersion();
		c.name = this.name;
		c.package_ = this.package_;
		c.version = this.version;
		c.oneFile = this.oneFile;
		c.url = this.url;
		c.sha1 = this.sha1;
		c.detectMSI = this.detectMSI;
		c.importantFileTitles.addAll(this.importantFileTitles);
		c.importantFilePaths.addAll(this.importantFilePaths);
		c.filePaths.addAll(this.filePaths);
		c.fileContents.addAll(this.fileContents);
		c.dependencyPackages.addAll(this.dependencyPackages);
		c.dependencyVersionRanges.addAll(this.dependencyVersionRanges);
		c.dependencyEnvVars.addAll(this.dependencyEnvVars);
		c.detectFilePaths.addAll(this.detectFilePaths);
		c.detectFileSHA1s.addAll(this.detectFileSHA1s);
		c.tags.addAll(this.tags);
		return c;
	}

	/**
	 * Creates <version>
	 * 
	 * @param d
	 *            XML document
	 * @return <version>
	 */
	public Element toXML(Document d) {
		PackageVersion pv = this;

		Element version = d.createElement("version");
		version.setAttribute("name", pv.version);
		version.setAttribute("package", pv.package_);
		if (pv.oneFile)
			version.setAttribute("type", "one-file");
		for (int i = 0; i < pv.importantFilePaths.size(); i++) {
			Element importantFile = d.createElement("important-file");
			version.appendChild(importantFile);
			importantFile.setAttribute("path", pv.importantFilePaths.get(i));
			importantFile.setAttribute("title", pv.importantFileTitles.get(i));
		}
		for (int i = 0; i < pv.filePaths.size(); i++) {
			Element file = d.createElement("file");
			version.appendChild(file);
			file.setAttribute("path", pv.filePaths.get(i));
			NWUtils.t(file, pv.getFileContents(i));
		}
		if (!pv.url.isEmpty())
			NWUtils.e(version, "url", pv.url);
		if (!pv.sha1.isEmpty())
			NWUtils.e(version, "sha1", pv.sha1);
		for (int i = 0; i < pv.dependencyPackages.size(); i++) {
			Element dependency = d.createElement("dependency");
			version.appendChild(dependency);
			dependency.setAttribute("package", pv.dependencyPackages.get(i));
			dependency.setAttribute("versions", pv.dependencyVersionRanges
					.get(i));
			if (!pv.dependencyEnvVars.get(i).isEmpty())
				NWUtils.e(dependency, "variable", pv.dependencyEnvVars.get(i));
		}
		if (!pv.detectMSI.isEmpty())
			NWUtils.e(version, "detect-msi", pv.detectMSI);
		for (int i = 0; i < pv.detectFilePaths.size(); i++) {
			Element detectFile = d.createElement("detect-file");
			version.appendChild(detectFile);
			NWUtils.e(detectFile, "path", pv.detectFilePaths.get(i));
			NWUtils.e(detectFile, "sha1", pv.detectFileSHA1s.get(i));
		}
		return version;
	}

	/**
	 * @param i
	 *            index of the file
	 * @return file contents <file>
	 */
	public String getFileContents(int i) {
		Object obj = fileContents.get(i);
		if (obj instanceof Text)
			return ((Text) obj).getValue();
		else
			return (String) obj;
	}

	@PostLoad
	public void postLoad() {
		if (this.sha1 == null)
			this.sha1 = "";

		// Bugfix: the content was stored as <String> which lead to the
		// conversion of long strings (> 500 characters) to Text and changing
		// their position in the list
		for (int i = 0; i < this.fileContents.size(); i++) {
			Object obj = this.fileContents.get(i);
			if (obj instanceof String)
				this.fileContents.set(i, new Text((String) obj));
		}
	}

	@PrePersist
	void onPersist() {
		DefaultServlet.dataVersion.incrementAndGet();
	}

	/**
	 * Removes the file with the specified index.
	 * 
	 * @param index
	 *            index of the file
	 */
	public void removeFile(int index) {
		this.filePaths.remove(index);
		this.fileContents.remove(index);
	}

	/**
	 * Changes the content of the specified <file>
	 * 
	 * @param index
	 *            index of the file
	 * @param content
	 *            file content
	 */
	public void setFileContents(int index, String content) {
		this.fileContents.set(index, new Text(content));
	}

	/**
	 * Adds a new <file>
	 * 
	 * @param path
	 *            file path
	 * @param content
	 *            file content
	 */
	public void addFile(String path, String content) {
		this.filePaths.add(path);
		this.fileContents.add(new Text(content));
	}

	/**
	 * @return created Key for this object
	 */
	public Key<PackageVersion> createKey() {
		return new Key<PackageVersion>(PackageVersion.class, this.name);
	}
}
